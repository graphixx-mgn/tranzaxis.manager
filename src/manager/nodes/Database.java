package manager.nodes;

import codex.command.EntityCommand;
import codex.log.Level;
import codex.mask.RegexMask;
import codex.model.Access;
import codex.model.Entity;
import codex.type.Bool;
import codex.type.Int;
import codex.type.Str;
import codex.utils.ImageUtils;
import codex.utils.Language;
import manager.commands.CheckDatabase;

public class Database extends Entity {

    public Database(String title) {
        super(ImageUtils.getByPath("/images/database.png"), title, null);
        
        model.addUserProp("dbUrl", 
                new Str(null).setMask(new RegexMask(
                        "(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5])):\\d{1,5}/\\w+", 
                        Language.get("dbUrl.error")
                )),
        true, Access.Select);
        model.addUserProp("dbSchema", new Str(null), true, null);
        model.addUserProp("dbPass", new Str(null), true, Access.Select);
        model.addDynamicProp("version", new Str(null), null, () -> {
            return model.getValue("dbSchema");
        },
        "dbSchema", "dbPass");
        //model.addUserProp("instanceId", new Int(null), true, Access.Select);
        model.addUserProp("layerURI", new Str(null), true, Access.Select);
        model.addUserProp("userNote", new Str(null), false, null);
        
        model.addUserProp("bool", new Bool(true), false, null);
        model.addUserProp("enum", new codex.type.Enum(Level.Debug), false, null);
        model.addUserProp("int", new Int(1000), false, null);
        
        addCommand(new CheckDatabase());
        addCommand(new TestCommand1());
        addCommand(new TestCommand2());
    }
    
    public class TestCommand1 extends EntityCommand {

        public TestCommand1() {
            super(
                    "test1", "Run test command #1",
                    ImageUtils.resize(ImageUtils.getByPath("/images/branch.png"), 28, 28), 
                    "Run test command #1",
                    (entity) -> true
            );
        }

        @Override
        public void execute(Entity context) {
            java.lang.System.err.println(toString()+": "+context);
        }
    }
    
    public class TestCommand2 extends EntityCommand {

        public TestCommand2() {
            super(
                    "test2", "Run test command #2",
                    ImageUtils.resize(ImageUtils.getByPath("/images/development.png"), 28, 28), 
                    "Run test command #2",
                    (entity) -> true
            );
        }

        @Override
        public void execute(Entity context) {
            java.lang.System.err.println(toString()+": "+context);
        }
    }
    
}
