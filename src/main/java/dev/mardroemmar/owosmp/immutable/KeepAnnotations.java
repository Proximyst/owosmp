package dev.mardroemmar.owosmp.immutable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Retention(RetentionPolicy.SOURCE)
@Value.Style(
    passAnnotations = {
      Nullable.class,
      NotNull.class,
    })
public @interface KeepAnnotations {}
