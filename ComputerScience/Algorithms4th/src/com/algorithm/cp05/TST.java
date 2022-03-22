package com.algorithm.cp05;

public class TST<Value> {

    private Node root;

    private class Node {
        char c;
        Node left, mid, right;
        Value val;
    }

    private Node get(Node x, String key, int d) {
        if (x == null) {
            return null;
        }
        char c = key.charAt(d);
        if (c > x.c) {
            return get(x.right, key, d );
        } else if (c < x.c) {
            return get(x.left, key, d );
        }else if (d < key.length() - 1){
            return get(x.mid, key, d + 1);
        }else {
            return x;
        }
    }

    private void put(Node x, String key, Value val, int d) {

    }

}
