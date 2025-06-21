package com.alchemedy.pharasnap.utils;

import java.util.ArrayList;

public class ArrayFunctions {
    public interface Mapper<R, I> {
        R map(I input);
    }
    public static <R, I> ArrayList<R> map(ArrayList<I> input, Mapper<R, I> mapper) {
        ArrayList<R> output = new ArrayList<>();
        for (I i : input) {
            output.add(mapper.map(i));
        }
        return output;
    }
}
