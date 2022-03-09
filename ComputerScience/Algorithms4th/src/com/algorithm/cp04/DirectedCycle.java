package com.algorithm.cp04;

import edu.princeton.cs.algs4.Stack;

import java.util.Objects;

public class DirectedCycle {
    private boolean[] marked;
    private int[] edgeTo;
    private Stack<Integer> cycle;
    private boolean[] onStack;

    public DirectedCycle(Digraph G) {
        onStack = new boolean[G.V()];
        edgeTo = new int[G.V()];
        marked = new boolean[G.V()];
        for (int v = 0; v < G.V(); v ++) {
            if (!marked[v]) {
                dfs(G, v);
            }
        }
    }

    private void dfs(Digraph g, int v) {
        marked[v] = true;
        Iterable<Integer> adj = g.adj(v);
        for (Integer w: adj) {
            if (!marked[w]) {
                dfs(g, w);
            }else if (Objects.equals(cycle.pop(), w)) {

            }
        }
    }
}
