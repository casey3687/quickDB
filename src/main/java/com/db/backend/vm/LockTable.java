package com.db.backend.vm;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.db.common.Error;
/**
 * 维护了一个依赖等待图，以进行死锁检测
 */
public class LockTable {
    
    private Map<Long, List<Long>> x2u;  // 某个XID已经获得的资源的UID列表
    private Map<Long, Long> u2x;        // UID被某个XID持有
    private Map<Long, List<Long>> wait; // 正在等待UID的XID列表
    private Map<Long, Lock> waitLock;   // 正在等待资源的XID的锁
    private Map<Long, Long> waitU;      // XID正在等待的UID
    private Lock lock;

    public LockTable() {
        x2u = new HashMap<>();
        u2x = new HashMap<>();
        wait = new HashMap<>();
        waitLock = new HashMap<>();
        waitU = new HashMap<>();
        lock = new ReentrantLock();
    }

    // 不需要等待则返回null，否则返回锁对象
    // 会造成死锁则抛出异常
    public Lock add(long xid, long uid) throws Exception {
        lock.lock();
        try {
            if(isInList(x2u, xid, uid)) {
                //已经在事务持有的资源列表中，不需要等待
                return null;
            }
            if(!u2x.containsKey(uid)) {
                //资源未被任何事务持有，将资源put进事务的持有列表中，无需等待
                u2x.put(uid, xid);
                putIntoList(x2u, xid, uid);
                return null;
            }
            //xid正在等待uid的列表
            waitU.put(xid, uid);
            //putIntoList(wait, xid, uid);
            //需要等待，将事务put进正在等待资源的列表中：uid -> List<xid>
            putIntoList(wait, uid, xid);
            //检测死锁
            if(hasDeadLock()) {
                waitU.remove(xid);
                removeFromList(wait, uid, xid);
                throw Error.DeadlockException;
            }
            //如果需要等待的话，会返回一个上了锁的 Lock 对象。
            //调用方在获取到该对象时，需要尝试获取该对象的锁，由此实现阻塞线程的目的
            Lock l = new ReentrantLock();
            l.lock();
            waitLock.put(xid, l);
            return l;
        } finally {
            lock.unlock();
        }
    }

    public void remove(long xid) {
        lock.lock();
        try {
            List<Long> l = x2u.get(xid);
            if(l != null) {
                while(!l.isEmpty()) {
                    Long uid = l.remove(0);
                    selectNewXID(uid);
                }
            }
            waitU.remove(xid);
            x2u.remove(xid);
            waitLock.remove(xid);

        } finally {
            lock.unlock();
        }
    }

    // 从等待队列中选择一个xid来占用uid
    private void selectNewXID(long uid) {
        u2x.remove(uid);
        List<Long> l = wait.get(uid);
        if(l == null) return;
        assert !l.isEmpty();

        while(!l.isEmpty()) {
            long xid = l.remove(0);
            if(!waitLock.containsKey(xid)) {
                continue;
            } else {
                u2x.put(uid, xid);
                Lock lo = waitLock.remove(xid);
                waitU.remove(xid);
                lo.unlock();
                break;
            }
        }

        if(l.isEmpty()) wait.remove(uid);
    }

    private Map<Long, Integer> xidStamp;
    private int stamp;

    // 检测是否存在死锁
    private boolean hasDeadLock() {
        Set<Long> visited = new HashSet<>();  // 全局已访问节点，避免重复遍历连通分量
        // 遍历所有可能的起始节点
        for(long xid : x2u.keySet()) {
            if(!visited.contains(xid)) {
                if(dfs(xid, visited)) {
                    return true;  // 发现环
                }
            }
        }
        return false;
    }

    // DFS检测环
    private boolean dfs(long xid, Set<Long> visited) {
        if(visited.contains(xid)) {
            return true;
        }
        visited.add(xid);
        Long uid = waitU.get(xid);
        if(uid == null) {
            return false;
        }
        Long holderXid = u2x.get(uid);
        if(holderXid == null) {
            return false;
        }
        return dfs(holderXid,  visited);
    }

    private void removeFromList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        List<Long> l = listMap.get(uid0);
        if(l == null) return;
        Iterator<Long> i = l.iterator();
        while(i.hasNext()) {
            long e = i.next();
            if(e == uid1) {
                i.remove();
                break;
            }
        }
        if(l.isEmpty()) {
            listMap.remove(uid0);
        }
    }

    private void putIntoList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        if(!listMap.containsKey(uid0)) {
            listMap.put(uid0, new ArrayList<>());
        }
        listMap.get(uid0).add(0, uid1);
    }

    private boolean isInList(Map<Long, List<Long>> listMap, long uid0, long uid1) {
        List<Long> l = listMap.get(uid0);
        if(l == null) return false;
        Iterator<Long> i = l.iterator();
        while(i.hasNext()) {
            long e = i.next();
            if(e == uid1) {
                return true;
            }
        }
        return false;
    }

}
