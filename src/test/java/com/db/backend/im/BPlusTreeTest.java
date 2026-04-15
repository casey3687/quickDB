package com.db.backend.im;

import org.junit.Test;
import com.db.backend.dm.DataManager;
import com.db.backend.dm.pagecache.PageCache;
import com.db.backend.tm.MockTransactionManager;
import com.db.backend.tm.TransactionManager;

import java.io.File;
import java.util.List;

public class BPlusTreeTest {
    private void cleanupFiles(String path) {
        new File(path + ".db").delete();
        new File(path + ".log").delete();
    }

    private String testPath(String name) {
        return "./tmp/" + name + "_" + System.nanoTime();
    }

    @Test
    public void testTreeSingle() throws Exception {
        String path = testPath("TestTreeSingle");
        cleanupFiles(path);
        TransactionManager tm = new MockTransactionManager();
        DataManager dm = DataManager.create(path, PageCache.PAGE_SIZE*10, tm);

        long root = BPlusTree.create(dm);
        BPlusTree tree = BPlusTree.load(root, dm);

        int lim = 10000;
        for(int i = lim-1; i >= 0; i --) {
            tree.insert(i, i);
        }

        for(int i = 0; i < lim; i ++) {
            List<Long> uids = tree.search(i);
            assert uids.size() == 1;
            assert uids.get(0) == i;
        }

        tree.close();
        dm.close();

        assert new File(path + ".db").delete();
        assert new File(path + ".log").delete();
    }
}
