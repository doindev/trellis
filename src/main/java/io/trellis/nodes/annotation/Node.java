package io.trellis.nodes.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/*
 * Annotation to mark a class as a Trellis workflow node.
 * Classes annotated with @Node will be automatically
 * discovered and registered in the node registry.
 */

@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Node {

	// Unique node type identifier
	String type();
	
	// display name for node
	String displayName();
	
	// subtitle template for node display
	String subtitle() default "";
	
	// node description
	String description() default "";
	
	// documentation url
	String documentationUrl() default "";
	
	// node category for grouping in UI
	String category() default "Utility";
	
	// this value will be used to limit the number of nodes visible in the node palette. Nodes with searchOnly=true will only be visible when searching by name.
	boolean searchOnly() default false;
	
	// this value will be used to group nodes into the 'Other' category that will be displayed at the bottom of the node palette. Nodes with other=true will be placed in the 'Other' category.
	boolean other() default false;
	
	// node version
	int version() default 1;
	
	// icon name or url
	String icon() default "";
	
	// is this a trigger node
	boolean trigger() default false;
	
	// this node supports polling
	boolean polling() default false;
	
	// credential types required by this node
	String[] credentials() default {};
	
	// node group
	String group() default "";
	
}
