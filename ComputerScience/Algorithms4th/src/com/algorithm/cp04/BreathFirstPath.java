
package com.algorithm.cp04;

import edu.princeton.cs.algs4.Queue;
import edu.princeton.cs.algs4.Stack;

/**
 * BFS 代码，可同时用于有向图和无向图
 */
public class BreathFirstPath {

    private boolean[] marked;
    private int[] edgeTo;
    private final int s;

    public BreathFirstPath(Graph graph, int s) {
        marked = new boolean[graph.V()];
        edgeTo = new int[graph.V()];
        this.s = s;
        bfs(graph, s);
    }

    /**
     * 使用循环实现
     *
     * 1. 标识 s
     * 2. 获取 s 的邻接表
     * 3. 标识邻接表中的所有元素
     * 4. 如果没有被标识，则仅需循环遍历，直到全部遍历完成
     * @param g
     * @param s
     */
    private void bfs(Graph g, int s) {
        Queue<Integer> q = new Queue<>();
        q.enqueue(s);
        this.marked[s] = true;
        while (!q.isEmpty()) {
            Integer v = q.dequeue();
            Iterable<Integer> adjs = g.adj(v);
            for (Integer w: adjs) {
                if (!marked[w]) {
                    q.enqueue(w);
                    marked[w] = true;
                    edgeTo[w] = v;
                }
            }
        }
    }

    public boolean hasPathTo(int v) {
        return marked[v];
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
