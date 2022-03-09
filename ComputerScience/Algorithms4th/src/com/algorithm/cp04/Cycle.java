package com.algorithm.cp04;

/**
 * 基于 DFS 判断一张图是否有环
 * <p>
 * 判断依据：深度遍历完成后，如果已经被标记，并且与起点相同，说明有环
 */
public class Cycle {

    private boolean[] marked;
    private boolean hasCycle;

    public Cycle(Graph graph) {
        marked = new boolean[graph.V()];

        for (int i = 0; i < graph.V(); i++) {
            if (!marked[i]) {
                dfs(graph, i, i);
            }
        }
    }

    /**
     * @param graph 图
     * @param v     当前遍历到的点
     * @param u     起点
     */
    private void dfs(Graph graph, int v, int u) {
        marked[v] = true;
        Iterable<Integer> adj = graph.adj(v);

        for (int w : adj) {
            if (!marked[w]) {
                dfs(graph, w, v);
                // 往下遍历到起点，说明有环
            } else if (w == u) {
                hasCycle = true;
                return;
            }
        }
    }

    public boolean hasCycle() {
        return this.hasCycle;
    }
}
