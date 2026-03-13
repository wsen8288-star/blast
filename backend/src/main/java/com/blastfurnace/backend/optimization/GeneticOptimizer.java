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

public class GeneticOptimizer {
    private static final double CROSSOVER_RATE = 0.8;
    private static final double MUTATION_RATE = 0.15;
    private static final double MUTATION_STD = 0.05;

    private final Random random = new Random();

    public EvolutionResult optimize(
            int generations,
            int populationSize,
            FitnessFunction fitnessFunction,
            ParameterRanges ranges,
            Function<Chromosome, OptimizationSolutionDTO> solutionMapper
    ) {
        return optimizeTopWithSeed(generations, populationSize, fitnessFunction, 1, null, ranges, solutionMapper);
    }

    public EvolutionResult optimizeTop(
            int generations,
            int populationSize,
            FitnessFunction fitnessFunction,
            int topK,
            ParameterRanges ranges,
            Function<Chromosome, OptimizationSolutionDTO> solutionMapper
    ) {
        return optimizeTopWithSeed(generations, populationSize, fitnessFunction, topK, null, ranges, solutionMapper);
    }

    public EvolutionResult optimizeTopWithSeed(
            int generations,
            int populationSize,
            FitnessFunction fitnessFunction,
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
            double[] fitnesses = computeFitnesses(population, fitnessFunction, ranges);
            population = evolvePopulation(population, fitnesses, fitnessFunction, ranges);
            FitnessStats stats = evaluatePopulation(population, fitnessFunction, ranges);
            maxFitnessHistory.add(stats.maxFitness());
            avgFitnessHistory.add(stats.avgFitness());
            // Record best solution of this generation
            Chromosome best = getBest(population, fitnessFunction, ranges);
            bestSolutionsHistory.add(solutionMapper.apply(best));
        }
        List<Chromosome> top = getTop(population, fitnessFunction, topK, ranges);
        List<OptimizationSolutionDTO> topSolutions = top.stream().map(solutionMapper).toList();
        return new EvolutionResult(topSolutions, maxFitnessHistory, avgFitnessHistory, bestSolutionsHistory);
    }

    private List<Chromosome> initializePopulation(int populationSize, Chromosome seed, ParameterRanges ranges) {
        List<Chromosome> population = new ArrayList<>(populationSize);
        if (seed != null) {
            if (isSafe(seed, ranges)) {
                population.add(seed);
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

    private List<Chromosome> evolvePopulation(List<Chromosome> population, double[] fitnesses, FitnessFunction fitnessFunction, ParameterRanges ranges) {
        List<Chromosome> newPopulation = new ArrayList<>(population.size());
        while (newPopulation.size() < population.size()) {
            Chromosome parentA = rouletteSelection(population, fitnesses);
            Chromosome parentB = rouletteSelection(population, fitnesses);
            List<Chromosome> children = crossover(parentA, parentB, ranges);
            for (Chromosome child : children) {
                mutate(child, ranges);
                newPopulation.add(enforceSafety(child, ranges));
                if (newPopulation.size() >= population.size()) {
                    break;
                }
            }
        }
        return newPopulation;
    }

    private Chromosome rouletteSelection(List<Chromosome> population, double[] fitnesses) {
        double total = 0.0;
        for (int i = 0; i < population.size(); i++) {
            double fitness = fitnesses[i];
            if (fitness > 0) {
                total += Math.max(fitness, 1e-6);
            }
        }
        if (total <= 0) {
            return population.get(random.nextInt(population.size())).copy();
        }
        double threshold = random.nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < population.size(); i++) {
            cumulative += Math.max(fitnesses[i], 1e-6);
            if (cumulative >= threshold) {
                return population.get(i).copy();
            }
        }
        return population.get(population.size() - 1).copy();
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

    private Chromosome getBest(List<Chromosome> population, FitnessFunction fitnessFunction, ParameterRanges ranges) {
        return population.stream()
                .max(Comparator.comparingDouble((Chromosome chromosome) -> safeFitness(chromosome, fitnessFunction, ranges)))
                .map(Chromosome::copy)
                .orElseGet(() -> randomChromosome(ranges));
    }

    private List<Chromosome> getTop(List<Chromosome> population, FitnessFunction fitnessFunction, int topK, ParameterRanges ranges) {
        return population.stream()
                .sorted(Comparator.comparingDouble((Chromosome chromosome) -> safeFitness(chromosome, fitnessFunction, ranges)).reversed())
                .limit(topK)
                .map(Chromosome::copy)
                .toList();
    }

    private FitnessStats evaluatePopulation(List<Chromosome> population, FitnessFunction fitnessFunction, ParameterRanges ranges) {
        double max = 0.0;
        double total = 0.0;
        for (Chromosome chromosome : population) {
            double fitness = safeFitness(chromosome, fitnessFunction, ranges);
            max = Math.max(max, fitness);
            total += fitness;
        }
        double avg = population.isEmpty() ? 0.0 : total / population.size();
        return new FitnessStats(max, avg);
    }

    private double[] computeFitnesses(List<Chromosome> population, FitnessFunction fitnessFunction, ParameterRanges ranges) {
        double[] fitnesses = new double[population.size()];
        for (int i = 0; i < population.size(); i++) {
            double fitness = safeFitness(population.get(i), fitnessFunction, ranges);
            fitnesses[i] = fitness;
        }
        return fitnesses;
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

    private double safeFitness(Chromosome chromosome, FitnessFunction fitnessFunction, ParameterRanges ranges) {
        if (!isSafe(chromosome, ranges)) {
            return 0.0;
        }
        return fitnessFunction.calculate(chromosome);
    }

    private double randomInRange(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record FitnessStats(double maxFitness, double avgFitness) {
    }
}
