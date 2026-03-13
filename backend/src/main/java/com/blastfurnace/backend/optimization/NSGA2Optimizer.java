package com.blastfurnace.backend.optimization;

import com.blastfurnace.backend.dto.EvolutionResult;
import com.blastfurnace.backend.dto.OptimizationSolutionDTO;
import com.blastfurnace.backend.service.IndustrialDataContract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

public class NSGA2Optimizer {
    private static final double CROSSOVER_RATE = 0.8;
    private static final double MUTATION_RATE = 0.15;
    private static final double MUTATION_STD = 0.05;

    private final Random random = new Random();

    public record Evaluation(double[] objectives, double constraintViolation, double preferenceFitness) {
    }

    public interface MultiObjectiveFunction {
        Evaluation evaluate(Chromosome chromosome);
    }

    public EvolutionResult optimizeTopWithSeed(
            int generations,
            int populationSize,
            MultiObjectiveFunction function,
            int topK,
            Chromosome seed,
            ParameterRanges ranges,
            Function<Chromosome, OptimizationSolutionDTO> solutionMapper
    ) {
        List<Chromosome> population = initializePopulation(populationSize, seed, ranges);
        List<Double> maxFitnessHistory = new ArrayList<>();
        List<Double> avgFitnessHistory = new ArrayList<>();
        List<OptimizationSolutionDTO> bestSolutionsHistory = new ArrayList<>();

        for (int i = 0; i < generations; i++) {
            List<Individual> parents = evaluatePopulation(population, function);
            assignRankAndCrowding(parents);
            List<Chromosome> offspring = reproduce(parents, populationSize, ranges, function);
            List<Individual> combined = new ArrayList<>(parents.size() + offspring.size());
            combined.addAll(parents);
            combined.addAll(evaluatePopulation(offspring, function));
            population = environmentalSelection(combined, populationSize);

            List<Individual> evaluated = evaluatePopulation(population, function);
            Individual best = evaluated.stream()
                    .filter(ind -> ind.constraintViolation <= 1e-12)
                    .max(Comparator.comparingDouble(ind -> ind.preferenceFitness))
                    .orElseGet(() -> evaluated.stream().max(Comparator.comparingDouble(ind -> ind.preferenceFitness)).orElse(null));
            if (best != null) {
                bestSolutionsHistory.add(solutionMapper.apply(best.chromosome));
            }
            double max = 0.0;
            double sum = 0.0;
            int count = 0;
            for (Individual ind : evaluated) {
                if (ind.constraintViolation <= 1e-12) {
                    max = Math.max(max, ind.preferenceFitness);
                    sum += ind.preferenceFitness;
                    count++;
                }
            }
            double avg = count > 0 ? sum / count : 0.0;
            maxFitnessHistory.add(max);
            avgFitnessHistory.add(avg);
        }

        List<Individual> finalPop = evaluatePopulation(population, function);
        assignRankAndCrowding(finalPop);
        List<Individual> firstFront = nonDominatedSort(finalPop).get(0);
        List<Individual> feasible = firstFront.stream().filter(ind -> ind.constraintViolation <= 1e-12).toList();
        Individual preferred = (feasible.isEmpty() ? firstFront : feasible).stream()
                .max(Comparator.comparingDouble(ind -> ind.preferenceFitness))
                .orElse(null);
        List<OptimizationSolutionDTO> topSolutions = new ArrayList<>();
        if (preferred != null) {
            topSolutions.add(solutionMapper.apply(preferred.chromosome));
        }
        firstFront.stream()
                .filter(ind -> preferred == null || ind != preferred)
                .sorted(Comparator.comparingDouble((Individual ind) -> ind.crowdingDistance).reversed())
                .limit(Math.max(0, topK - topSolutions.size()))
                .forEach(ind -> topSolutions.add(solutionMapper.apply(ind.chromosome)));
        return new EvolutionResult(topSolutions, maxFitnessHistory, avgFitnessHistory, bestSolutionsHistory);
    }

    private static final class Individual {
        private final Chromosome chromosome;
        private final double[] objectives;
        private final double constraintViolation;
        private final double preferenceFitness;
        private int rank;
        private double crowdingDistance;

        private Individual(Chromosome chromosome, Evaluation evaluation) {
            this.chromosome = chromosome;
            this.objectives = evaluation.objectives();
            this.constraintViolation = evaluation.constraintViolation();
            this.preferenceFitness = evaluation.preferenceFitness();
            this.rank = Integer.MAX_VALUE;
            this.crowdingDistance = 0.0;
        }
    }

    private List<Individual> evaluatePopulation(List<Chromosome> population, MultiObjectiveFunction function) {
        List<Individual> list = new ArrayList<>(population.size());
        for (Chromosome chromosome : population) {
            Evaluation eval = function.evaluate(chromosome);
            list.add(new Individual(chromosome, eval));
        }
        return list;
    }

    private void assignRankAndCrowding(List<Individual> population) {
        List<List<Individual>> fronts = nonDominatedSort(population);
        int rank = 0;
        for (List<Individual> front : fronts) {
            for (Individual ind : front) {
                ind.rank = rank;
            }
            computeCrowdingDistance(front);
            rank++;
        }
    }

    private List<Chromosome> environmentalSelection(List<Individual> combined, int populationSize) {
        List<List<Individual>> fronts = nonDominatedSort(combined);
        List<Chromosome> next = new ArrayList<>(populationSize);
        for (List<Individual> front : fronts) {
            computeCrowdingDistance(front);
            if (next.size() + front.size() <= populationSize) {
                for (Individual ind : front) {
                    next.add(ind.chromosome.copy());
                }
            } else {
                front.sort(Comparator.comparingDouble((Individual ind) -> ind.crowdingDistance).reversed());
                int remain = populationSize - next.size();
                for (int i = 0; i < remain && i < front.size(); i++) {
                    next.add(front.get(i).chromosome.copy());
                }
                break;
            }
        }
        return next;
    }

    private List<Chromosome> reproduce(List<Individual> parents, int targetSize, ParameterRanges ranges, MultiObjectiveFunction function) {
        List<Chromosome> offspring = new ArrayList<>(targetSize);
        while (offspring.size() < targetSize) {
            Individual p1 = tournamentSelect(parents);
            Individual p2 = tournamentSelect(parents);
            List<Chromosome> children = crossover(p1.chromosome, p2.chromosome, ranges);
            for (Chromosome child : children) {
                mutate(child, ranges);
                offspring.add(enforceSafety(child, ranges));
                if (offspring.size() >= targetSize) {
                    break;
                }
            }
        }
        return offspring;
    }

    private Individual tournamentSelect(List<Individual> population) {
        Individual a = population.get(random.nextInt(population.size()));
        Individual b = population.get(random.nextInt(population.size()));
        if (a.rank != b.rank) {
            return a.rank < b.rank ? a : b;
        }
        if (a.crowdingDistance != b.crowdingDistance) {
            return a.crowdingDistance > b.crowdingDistance ? a : b;
        }
        return a.preferenceFitness >= b.preferenceFitness ? a : b;
    }

    private List<List<Individual>> nonDominatedSort(List<Individual> population) {
        int size = population.size();
        List<List<Integer>> dominates = new ArrayList<>(size);
        int[] dominatedCount = new int[size];
        for (int i = 0; i < size; i++) {
            dominates.add(new ArrayList<>());
            dominatedCount[i] = 0;
        }

        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                int dom = dominanceCompare(population.get(i), population.get(j));
                if (dom < 0) {
                    dominates.get(i).add(j);
                    dominatedCount[j]++;
                } else if (dom > 0) {
                    dominates.get(j).add(i);
                    dominatedCount[i]++;
                }
            }
        }

        List<List<Individual>> fronts = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (dominatedCount[i] == 0) {
                current.add(i);
            }
        }

        while (!current.isEmpty()) {
            List<Individual> front = new ArrayList<>(current.size());
            List<Integer> next = new ArrayList<>();
            for (int idx : current) {
                front.add(population.get(idx));
                for (int q : dominates.get(idx)) {
                    dominatedCount[q]--;
                    if (dominatedCount[q] == 0) {
                        next.add(q);
                    }
                }
            }
            fronts.add(front);
            current = next;
        }
        if (fronts.isEmpty()) {
            fronts.add(new ArrayList<>(population));
        }
        return fronts;
    }

    private int dominanceCompare(Individual a, Individual b) {
        boolean feasibleA = a.constraintViolation <= 1e-12;
        boolean feasibleB = b.constraintViolation <= 1e-12;
        if (feasibleA && !feasibleB) {
            return -1;
        }
        if (!feasibleA && feasibleB) {
            return 1;
        }
        if (!feasibleA && !feasibleB) {
            return Double.compare(a.constraintViolation, b.constraintViolation);
        }
        boolean aBetter = false;
        boolean bBetter = false;
        int dims = Math.min(a.objectives.length, b.objectives.length);
        for (int i = 0; i < dims; i++) {
            double av = a.objectives[i];
            double bv = b.objectives[i];
            if (av < bv) {
                aBetter = true;
            } else if (av > bv) {
                bBetter = true;
            }
        }
        if (aBetter && !bBetter) {
            return -1;
        }
        if (!aBetter && bBetter) {
            return 1;
        }
        return 0;
    }

    private void computeCrowdingDistance(List<Individual> front) {
        int n = front.size();
        if (n == 0) {
            return;
        }
        for (Individual ind : front) {
            ind.crowdingDistance = 0.0;
        }
        int m = front.get(0).objectives.length;
        for (int obj = 0; obj < m; obj++) {
            int idx = obj;
            front.sort(Comparator.comparingDouble(ind -> ind.objectives[idx]));
            front.get(0).crowdingDistance = Double.POSITIVE_INFINITY;
            front.get(n - 1).crowdingDistance = Double.POSITIVE_INFINITY;
            double min = front.get(0).objectives[idx];
            double max = front.get(n - 1).objectives[idx];
            double span = max - min;
            if (span <= 1e-12) {
                continue;
            }
            for (int i = 1; i < n - 1; i++) {
                double prev = front.get(i - 1).objectives[idx];
                double next = front.get(i + 1).objectives[idx];
                front.get(i).crowdingDistance += (next - prev) / span;
            }
        }
    }

    private List<Chromosome> initializePopulation(int populationSize, Chromosome seed, ParameterRanges ranges) {
        List<Chromosome> population = new ArrayList<>(populationSize);
        if (seed != null) {
            if (isSafe(seed, ranges)) {
                population.add(seed.copy());
            }
            int seedVariations = populationSize / 3;
            for (int i = 0; i < seedVariations; i++) {
                Chromosome variant = seed.copy();
                mutate(variant, ranges);
                population.add(enforceSafety(variant, ranges));
            }
        }
        while (population.size() < populationSize) {
            Chromosome candidate = randomChromosome(ranges);
            population.add(enforceSafety(candidate, ranges));
        }
        return population;
    }

    private List<Chromosome> crossover(Chromosome parentA, Chromosome parentB, ParameterRanges ranges) {
        List<Chromosome> children = new ArrayList<>(2);
        if (random.nextDouble() > CROSSOVER_RATE) {
            children.add(parentA.copy());
            children.add(parentB.copy());
            return children;
        }
        List<String> keys = ranges.keys();
        if (keys.size() < 2) {
            children.add(parentA.copy());
            children.add(parentB.copy());
            return children;
        }
        int point = random.nextInt(keys.size() - 1) + 1;
        Map<String, Double> genesA = new HashMap<>();
        Map<String, Double> genesB = new HashMap<>();
        for (String key : keys) {
            genesA.put(key, parentA.getGene(key));
            genesB.put(key, parentB.getGene(key));
        }
        for (int i = point; i < keys.size(); i++) {
            String key = keys.get(i);
            Double temp = genesA.get(key);
            genesA.put(key, genesB.get(key));
            genesB.put(key, temp);
        }
        children.add(new Chromosome(genesA));
        children.add(new Chromosome(genesB));
        return children;
    }

    private void mutate(Chromosome chromosome, ParameterRanges ranges) {
        List<String> keys = ranges.keys();
        for (String key : keys) {
            if (random.nextDouble() < MUTATION_RATE) {
                ParameterRanges.Range range = ranges.getRange(key);
                if (range == null || range.max() <= range.min()) {
                    continue;
                }
                Double currentBoxed = chromosome.getGene(key);
                double current = currentBoxed != null ? currentBoxed : randomInRange(range.min(), range.max());
                double span = Math.max(1e-9, range.max() - range.min());
                double delta = random.nextGaussian() * span * MUTATION_STD;
                double next = clamp(current + delta, range.min(), range.max());
                chromosome.setGene(key, next);
            }
        }
        if (!isSafe(chromosome, ranges)) {
            Chromosome safe = enforceSafety(chromosome, ranges);
            chromosome.getGenes().clear();
            chromosome.getGenes().putAll(safe.getGenes());
        }
    }

    private Chromosome randomChromosome(ParameterRanges ranges) {
        Map<String, Double> genes = new HashMap<>();
        for (String key : ranges.keys()) {
            ParameterRanges.Range r = ranges.getRange(key);
            if (r == null) {
                continue;
            }
            genes.put(key, randomInRange(r.min(), r.max()));
        }
        return new Chromosome(genes);
    }

    private boolean isSafe(Chromosome chromosome, ParameterRanges ranges) {
        if (chromosome == null) {
            return false;
        }
        for (String key : ranges.keys()) {
            ParameterRanges.Range range = ranges.getRange(key);
            if (range == null) {
                continue;
            }
            Double value = chromosome.getGene(key);
            if (value == null) {
                continue;
            }
            IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(key);
            if (spec == null) {
                if (value < range.min() || value > range.max()) {
                    return false;
                }
                continue;
            }
            double minSafe = Math.max(range.min(), spec.warningMin());
            double maxSafe = Math.min(range.max(), spec.warningMax());
            if (minSafe > maxSafe) {
                minSafe = range.min();
                maxSafe = range.max();
            }
            if (value < minSafe || value > maxSafe) {
                return false;
            }
        }
        return true;
    }

    private Chromosome enforceSafety(Chromosome chromosome, ParameterRanges ranges) {
        Chromosome candidate = chromosome == null ? randomChromosome(ranges) : chromosome.copy();
        for (int attempts = 0; attempts < 16; attempts++) {
            if (isSafe(candidate, ranges)) {
                return candidate;
            }
            Chromosome repaired = repairToSafe(candidate, ranges);
            if (isSafe(repaired, ranges)) {
                return repaired;
            }
            candidate = randomChromosome(ranges);
        }
        Chromosome fallback = repairToSafe(candidate, ranges);
        if (isSafe(fallback, ranges)) {
            return fallback;
        }
        return defaultSafeChromosome(ranges);
    }

    private Chromosome repairToSafe(Chromosome chromosome, ParameterRanges ranges) {
        Chromosome candidate = chromosome == null ? randomChromosome(ranges) : chromosome.copy();
        for (String key : ranges.keys()) {
            ParameterRanges.Range range = ranges.getRange(key);
            if (range == null) {
                continue;
            }
            Double value = candidate.getGene(key);
            if (value == null) {
                continue;
            }
            IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(key);
            double minSafe = range.min();
            double maxSafe = range.max();
            if (spec != null) {
                minSafe = Math.max(minSafe, spec.warningMin());
                maxSafe = Math.min(maxSafe, spec.warningMax());
                if (minSafe > maxSafe) {
                    minSafe = range.min();
                    maxSafe = range.max();
                }
            }
            candidate.setGene(key, clamp(value, minSafe, maxSafe));
        }
        return candidate;
    }

    private Chromosome defaultSafeChromosome(ParameterRanges ranges) {
        Map<String, Double> genes = new HashMap<>();
        for (String key : ranges.keys()) {
            ParameterRanges.Range r = ranges.getRange(key);
            if (r == null) {
                continue;
            }
            IndustrialDataContract.ParameterSpec spec = IndustrialDataContract.findByAnyKey(key);
            double minSafe = r.min();
            double maxSafe = r.max();
            if (spec != null) {
                minSafe = Math.max(minSafe, spec.warningMin());
                maxSafe = Math.min(maxSafe, spec.warningMax());
                if (minSafe > maxSafe) {
                    minSafe = r.min();
                    maxSafe = r.max();
                }
            }
            genes.put(key, (minSafe + maxSafe) / 2.0);
        }
        return new Chromosome(genes);
    }

    private double randomInRange(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
