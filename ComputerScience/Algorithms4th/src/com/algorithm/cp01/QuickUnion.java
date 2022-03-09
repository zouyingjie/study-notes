package com.algorithm.cp01;


public class QuickUnion implements UF {
    private int[] id;
    private int count;

    public QuickUnion(int n) {
        this.id = new int[n];
        for (int i = 0; i < n; i++) {
            id[i] = i;
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
