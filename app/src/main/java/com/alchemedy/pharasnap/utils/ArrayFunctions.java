package com.alchemedy.pharasnap.utils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ArrayFunctions {
    public interface Mapper<R, I> {
        R map(I input);
    }

    public interface BooleanMapper<I> {
        boolean map(I input);
    }
    public static <R, I> ArrayList<R> map(ArrayList<I> input, Mapper<R, I> mapper) {
        ArrayList<R> output = new ArrayList<>();
        for (I i : input) {
            output.add(mapper.map(i));
        }
        return output;
    }

    public static <I> @Nullable I findOne(I[] input, BooleanMapper<I> mapper) {
        for (I i : input) {
            if(mapper.map(i))
                return i;
        }
        return null;
    }
}
