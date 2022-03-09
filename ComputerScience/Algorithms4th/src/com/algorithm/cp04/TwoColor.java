package com.algorithm.cp04;

public class TwoColor {

    private boolean[] marked;
    private boolean isTwoColor;
    private boolean[] color;

    public TwoColor(Graph graph) {
        marked = new boolean[graph.V()];
        color = new boolean[graph.V()];
        for (int s = 0; s < graph.V(); s ++) {
            if (!marked[s]) {
                dfs(graph, s);
            }
        }
    }

    private void dfs(Graph g, int v) {
        marked[v] = true;
        for (int w: g.adj(v)) {
            if (!marked[w]) {
                color[w] = !color[v];
                dfs(g, w);
            }else if (color[w] == color[v]) {
                isTwoColor = false;
            }
        }
    }

    private boolean isBipartite() {
        return this.isTwoColor;
    }
}
