package com.db.backend.server;

import com.db.backend.parser.statement.Begin;
import com.db.backend.parser.statement.Create;
import com.db.backend.parser.statement.Delete;
import com.db.backend.parser.statement.Drop;
import com.db.backend.parser.statement.Insert;
import com.db.backend.parser.statement.Select;
import com.db.backend.parser.statement.Update;
import com.db.backend.tbm.BeginRes;
import com.db.backend.tbm.TableManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExecutorDropTest {

    @Test
    public void shouldExecuteDropTable() throws Exception {
        Executor executor = new Executor(new MockTableManager());
        byte[] res = executor.execute("drop table user".getBytes());
        assertEquals("drop user", new String(res));
    }

    private static class MockTableManager implements TableManager {
        @Override
        public BeginRes begin(Begin begin) {
            BeginRes res = new BeginRes();
            res.xid = 1;
            res.result = "begin".getBytes();
            return res;
        }

        @Override
        public byte[] commit(long xid) {
            return "commit".getBytes();
        }

        @Override
        public byte[] abort(long xid) {
            return "abort".getBytes();
        }

        @Override
        public byte[] show(long xid) {
            return new byte[0];
        }

        @Override
        public byte[] create(long xid, Create create) {
            return new byte[0];
        }

        @Override
        public byte[] drop(long xid, Drop drop) {
            return ("drop " + drop.tableName).getBytes();
        }

        @Override
        public byte[] insert(long xid, Insert insert) {
            return new byte[0];
        }

        @Override
        public byte[] read(long xid, Select select) {
            return new byte[0];
        }

        @Override
        public byte[] update(long xid, Update update) {
            return new byte[0];
        }

        @Override
        public byte[] delete(long xid, Delete delete) {
            return new byte[0];
        }
    }
}
