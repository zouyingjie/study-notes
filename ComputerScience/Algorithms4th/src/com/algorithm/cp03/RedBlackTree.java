package com.algorithm.cp03;



public class RedBlackTree<Key extends Comparable, Value> {

    private static final boolean RED = true;
    private static final boolean BLACK = false;

    private class Node {
        Key key;
        Value value;
        Node left, right;
        int N;
        boolean color;

        Node(Key key, Value value, int N, boolean color) {
            this.key = key;
            this.value = value;
            this.N = N;
            this.color = color;
        }
    }

    private boolean isRed(Node node) {
        if (node == null) {
            return false;
        }
        return node.color == RED;
    }

    private Node root;

    // 左旋
    private Node rotateLeft() {
        return null;
    }

    // 右旋
    private Node rotateRight() {
        return null;
    }

    private void filpColors(Node h) {
    }

    public void put(Key key, Value value) {

    }

    private Node put(Node h, Key key, Value value) {
        if (h == null) {
            return new Node(key, value, 1, RED);
        }

        int cmp = key.compareTo(h.key);
        if (cmp > 0) {
            h.right = put(h.right, key, value);
        }else if (cmp < 0){
            h.left = put(h.left, key, value);
        }else {
            h.value = value;
        }

        // 旋转: TODO
        // 1. 右边是红连接，左边是黑连接， 左旋

        if (isRed(h.right) && isRed(h.left)) {
            rotateLeft();
        }

        // 2. 连续两条红连接，右旋
        if (isRed(h.left) && isRed(h.left.left)) {
            rotateRight();
        }
        // 3. 两条都是红连接，转换颜色
        if (isRed(h.left) && isRed(h.right)){
            filpColors(h);
        }

        h.N = size(h.left) + size(h.right) + 1;
        return h;
    }

    private int size(Node node) {
        if (node == null) {
            return 0;
        }
        return node.N;
    }
}
