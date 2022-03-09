package com.algorithm.cp01;

/**
 * Quick Find 算法：
 * <p>
 * an eager approach，以数据建模，如果两个点相连，那么索引对应的值相等
 *
 * 初始化操作：O(N)
 * union: O(N)
 * find: O(1)
 */
public class QuickFind implements UF {
    private int[] id;
    private int count;

    public QuickFind(int n) {
        this.id = new int[n];
        for (int i = 0; i < n; i++) {
            id[i] = i;
        }
        this.count = n;
    }

    @Override
    public void union(int p, int q) {

        int qid = id[q];
        int pid = id[p];

        if (pid == qid) {
            return;
        }

        for (int i = 0; i < id.length; i++) {
            if (id[i] == pid) {
                id[i] = qid;
            }
        }

        count --;

    }

    @Override
    public int find(int p) {
        return id[p];
    }

    @Override
    public boolean connected(int p, int q) {
        return id[p] == id[q];
    }

    @Override
    public int count() {
        return this.count;
    }
}
