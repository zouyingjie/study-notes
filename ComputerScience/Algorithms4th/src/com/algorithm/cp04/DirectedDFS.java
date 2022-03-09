package com.algorithm.cp04;

/**
 * 有向图的 DFS 可用来做垃圾回收的可达性判断，从而实现标记清楚
 * Mark-sweep Algorithm
 */
public class DirectedDFS {

    private boolean[] marked;

    public DirectedDFS(Digraph digraph, int s) {
        marked = new boolean[digraph.V()];
        dfs(digraph, s);
    }

    private void dfs(Digraph g, int v) {

        marked[v] = true;
        Iterable<Integer> adj = g.adj(v);
        for (Integer w : adj) {
            if (!marked[w]) {
                dfs(g, w);
            }
        }
    }

    public boolean visited(int v) {
        return marked[v];
    }


}
