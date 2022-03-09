package com.zyj.c094

type TreeNode struct {
	Val   int
	Left  *TreeNode
	Right *TreeNode
}

func inorderTraversal(root *TreeNode) []int {
	result := []int{}
	var inOrder = func(node *TreeNode){}
	inOrder = func (node *TreeNode)  {
		if node == nil {
			return
		}
		inOrder(node.Left)
		result = append(result, node.Val)
		inOrder(node.Right)
	}
	inOrder(root)
	return result
}
