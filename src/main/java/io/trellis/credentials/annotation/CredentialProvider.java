package io.trellis.credentials.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * Annotation to mark a class as a Trellis credential provider.
 * Classes annotated with @CredentialProvider will be automatically
 * discovered and registered in the credential type registry.
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CredentialProvider {

    /** Unique credential type identifier */
    String type();

    /** Display name shown in UI */
    String displayName();

    /** Short description of this credential type */
    String description() default "";

    /** Category for grouping in UI (e.g. "Cloud Services", "Communication") */
    String category() default "Generic";

    /** Icon name or URL */
    String icon() default "";

    /** Documentation URL */
    String documentationUrl() default "";

    /** Parent credential type this extends (for inheritance) */
    String extendsType() default "";
}
