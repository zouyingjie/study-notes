package com.algorithm.cp04;

/**
 * 基于 DFS 计算连通分量
 */
public class CC {

    private boolean[] marked;
    private int[] id;
    private int count;

    public CC(Graph graph, int s) {
        marked = new boolean[graph.V()];
        id = new int[graph.V()];

        for (int i = 0; i < graph.V(); i++) {
            if (!marked[i]) {
                dfs(graph, i);
                count++;
            }
        }
        dfs(graph, s);
    }

    private void dfs(Graph g, int v) {
        marked[v] = true;
        id[v] = count;
        for (int w : g.adj(v)) {
            if (!marked[w]) {
                dfs(g, w);
            }
        }
    }

    public boolean connected(int w, int v) {
        return id[w] == id[v];
    }

    public int count() {
        return count;
    }

}

