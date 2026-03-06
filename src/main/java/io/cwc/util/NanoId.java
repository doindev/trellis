package io.cwc.util;

import org.hibernate.annotations.IdGeneratorType;
import java.lang.annotation.*;

@IdGeneratorType(NanoIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface NanoId {}
