package com.blastfurnace.backend.optimization;

@FunctionalInterface
public interface FitnessFunction {
    double calculate(Chromosome chromosome);
}
