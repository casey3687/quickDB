package com.db.backend.vm;

import com.db.common.Error;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockTable {

    private Map<Long, List<Long>> x2u;
    private Map<Long, Long> u2x;
    private Map<Long, List<Long>> wait;
    private Map<Long, Lock> waitLock;
    private Map<Long, Long> waitU;
    private Lock lock;

    public LockTable() {
        x2u = new HashMap<>();
        u2x = new HashMap<>();
        wait = new HashMap<>();
        waitLock = new HashMap<>();
        waitU = new HashMap<>();
        lock = new ReentrantLock();
    }

    public Lock add(long xid, long uid) throws Exception {
        lock.lock();
        try {
            if(isInList(x2u, xid, uid)) {
                return null;
            }
            if(!u2x.containsKey(uid)) {
                u2x.put(uid, xid);
                putIntoList(x2u, xid, uid);
                return null;
            }

            waitU.put(xid, uid);
            putIntoList(wait, uid, xid);
            if(hasDeadLock()) {
                waitU.remove(xid);
                removeFromList(wait, uid, xid);
                throw Error.DeadlockException;
            }

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

    private void selectNewXID(long uid) {
        u2x.remove(uid);
        List<Long> l = wait.get(uid);
        if(l == null) return;
        assert !l.isEmpty();

        while(!l.isEmpty()) {
            long xid = l.remove(0);
            if(!waitLock.containsKey(xid)) {
                continue;
            }
            u2x.put(uid, xid);
            Lock lo = waitLock.remove(xid);
            waitU.remove(xid);
            putIntoList(x2u, xid, uid);
            lo.unlock();
            break;
        }

        if(l.isEmpty()) {
            wait.remove(uid);
        }
    }

    private boolean hasDeadLock() {
        Set<Long> checked = new HashSet<>();
        for(long xid : x2u.keySet()) {
            if(dfs(xid, checked, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    private boolean dfs(long xid, Set<Long> checked, Set<Long> path) {
        if(path.contains(xid)) {
            return true;
        }
        if(checked.contains(xid)) {
            return false;
        }

        path.add(xid);
        Long uid = waitU.get(xid);
        if(uid != null) {
            Long holderXid = u2x.get(uid);
            if(holderXid != null && dfs(holderXid, checked, path)) {
                return true;
            }
        }
        path.remove(xid);
        checked.add(xid);
        return false;
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
