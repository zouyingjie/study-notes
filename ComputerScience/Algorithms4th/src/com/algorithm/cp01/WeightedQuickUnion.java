package com.algorithm.cp01;


public class WeightedQuickUnion implements UF {
    private int[] id;
    private int[] sz;
    private int count;

    public WeightedQuickUnion(int n) {
        this.id = new int[n];
        this.sz = new int[n];
        for (int i = 0; i < n; i++) {
            id[i] = i;
            sz[i] = 1;
        }
        this.count = n;
    }

    @Override
    public void union(int p, int q) {

        int pRoot = find(p);
        int qRoot = find(q);
        if (qRoot == pRoot) {
            return;
        }

        id[pRoot] = qRoot;
        count--;

    }

    @Override
    public int find(int p) {
        while (p != id[p]) {
            p = id[p];
        }
        return p;
    }

    @Override
    public boolean connected(int p, int q) {

        int pRoot = find(p);
        int qRoot = find(q);
        return pRoot == qRoot;
    }

    @Override
    public int count() {
        return this.count;
    }
}
