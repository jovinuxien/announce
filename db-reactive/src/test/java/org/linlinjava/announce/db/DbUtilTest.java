package org.linlinjava.announce.db;

import org.junit.jupiter.api.Test;
import org.linlinjava.announce.db.util.DbUtil;

import java.io.File;

public class DbUtilTest {
    @Test
    public void testBackup() {
        File file = new File("test.sql");
        DbUtil.backup(file, "litemall", "Calliste_1006", "litemall");
    }

//    这个测试用例会重置litemall数据库，所以比较危险，请开发者注意
//    @Test
    public void testLoad() {
        File file = new File("test.sql");
        DbUtil.load(file, "litemall", "Calliste_1006", "litemall");
    }
}
