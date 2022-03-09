package com.algorithm.cp04;

import edu.princeton.cs.algs4.Stack;

public class DepthFirstPaths {

    private boolean[] marked;
    private int[] edgeTo;
    private int count;
    private final int s;

    public DepthFirstPaths(Graph graph, int s) {
        marked = new boolean[graph.V()];
        edgeTo = new int[graph.V()];
        this.s = s;
        dfs(graph, s);
    }

    private void dfs(Graph g, int v) {
        marked[v] = true;
        count++;
        for (int w : g.adj(v)) {
            if (!marked(w)) {
                edgeTo[w] = v;
                dfs(g, w);
            }
        }
    }

    public boolean marked(int w) {
        return this.marked(w);
    }

    public int count() {
        return count;
    }

    public boolean hasPathTo(int v) {
        return this.marked(v);
    }

    public Iterable<Integer> path(int v) {
        if (!hasPathTo(v)) {
            return null;
        }
        Stack<Integer> path = new Stack<>();
        for (int x = v; x != s; x = edgeTo[x]) {
            path.push(x);
        }
        path.push(s);
        return path;
    }
}
