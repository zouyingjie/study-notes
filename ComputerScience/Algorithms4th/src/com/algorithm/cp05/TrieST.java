package com.algorithm.cp05;

import edu.princeton.cs.algs4.Queue;

public class TrieST<Value> {

    private static int R = 256;
    private Node root;

    private static class Node {
        Object val;
        private Node[] next = new Node[R];
    }

    public Value get(String key) {
        Node node = get(root, key, 0);
        if (node == null) {
            return null;
        }
        return (Value) node.val;
    }

    private Node get(Node x, String key, int d) {
        if (x == null) {
            return null;
        }

        if (d == key.length()) {
            return x;
        }
        char c = key.charAt(d);
        return get(x.next[c], key, d + 1);
    }

    public void put(String key, Value val) {
        root = put(root, key, val, 0);
    }

    /**
     * 1. x 为空，则新建
     * 2. 如果当前达到了 key 的最后一个字符，表示这就是尾节点，赋值即可
     * 3. 如果不是，则继续向后查找添加
     * @param x
     * @param key
     * @param val
     * @param d
     */
    private Node put(Node x, String key, Value val, int d) {
        if (x == null) {
            x = new Node();
        }

        if (d == key.length()) {
            x.val = val;
            return x;
        }

        char c = key.charAt(d);
        x.next[c] = put(x.next[c], key, val, d + 1);
        return x;
    }

    public Iterable<String> keys() {
        return keyWithPrefix("");
    }

    public Iterable<String> keyWithPrefix(String pre) {
        Queue<String> q = new Queue<>();
        Node node = get(root, pre, 0);
        collector(node, pre, q);
        return q;
    }

    private void collector(Node x, String pre, Queue queue) {
        if (x == null) {
            return;
        }

        if (x != null) {
            queue.enqueue(pre);
        }

        for (char c = 0; c < R; c ++) {
            collector(x.next[c], pre + c, queue);
        }
    }
}
