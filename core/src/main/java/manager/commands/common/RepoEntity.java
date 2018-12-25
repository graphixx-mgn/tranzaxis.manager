package manager.commands.common;

import codex.model.Catalog;

/**
 *
 * @author igredyaev
 */
class RepoEntity extends Catalog {

    RepoEntity(String repoDirName) {
        super(null, null, repoDirName, null);
    }

    @Override
    public Class getChildClass() {
        return EntryEntity.class;
    }

    @Override
    public boolean allowModifyChild() {
        return false;
    }

}