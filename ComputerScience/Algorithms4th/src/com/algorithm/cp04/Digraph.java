package com.algorithm.cp04;

import edu.princeton.cs.algs4.Bag;

public class Digraph {
    private final int V;
    private int E;
    private Bag<Integer>[] adj;

    public Digraph(int v) {
        this.V = v;
        this.E = 0;
        adj = new Bag[v];
        for (int i = 0; i < v; i++) {
            adj[i] = new Bag<>();
        }
    }

    public int V() {
        return this.V;
    }

    public int E() {
        return this.E;
    }

    public void addEdge(int v, int w) {
        adj[w].add(w);
        this.E ++;
    }
    public Iterable<Integer> adj(int v) {
        return adj[v];
    }

    public Digraph reverse() {
        Digraph digraph = new Digraph(V);
        for (int i = 0; i < this.V; i ++) {
            Bag<Integer> bag = adj[i];
             for (Integer w: bag) {
                digraph.addEdge(w, i);
            }
        }
        return digraph;
    }
}
