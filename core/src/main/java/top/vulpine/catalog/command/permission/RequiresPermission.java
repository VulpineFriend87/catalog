package top.vulpine.catalog.command.permission;

import revxrsal.commands.annotation.DistributeOnMethods;
import revxrsal.commands.annotation.NotSender;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a command as requiring a Catalog permission node.
 *
 * <p>The node is below {@code catalog.}: {@code command.list} is {@code catalog.command.list}.
 * {@link top.vulpine.catalog.platform.Viewer#has} checks it.</p>
 */
@DistributeOnMethods
@NotSender.ImpliesNotSender
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * @return the node below {@code catalog.}
     */
    String value();

}
