package com.agro.terrainservice.utils;

import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class FieldFilter {
    public static SimpleFilterProvider filter(String fields, String filterId) {
        SimpleBeanPropertyFilter filter;
        if (fields != null && !fields.trim().isEmpty()) {
            Set<String> requestedFields = Arrays.stream(fields.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            filter = SimpleBeanPropertyFilter.filterOutAllExcept(requestedFields);
        } else {
            filter = SimpleBeanPropertyFilter.serializeAll();
        }
        return new SimpleFilterProvider().addFilter(filterId, filter);
    }
}
