package manager.commands.common.report;

import manager.nodes.RepositoryBranch;

import java.lang.annotation.*;

@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BranchLink {
    Class<? extends RepositoryBranch> branchCatalogClass() default RepositoryBranch.class;
    int priority() default 0;
}
