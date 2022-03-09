class TreeNode:
    def __init__(self, val=0, left=None, right=None):
        self.val = val
        self.left = left
        self.right = right

class Solution:
    def inorderTraversal(self, root: TreeNode) -> List[int]:
        result = []
        def inOrder(node: TreeNode):
            if node is None:
                return
            inOrder(node.left)
            result.append(node.val)
            inOrder(node.right)

        inOrder(root)
        return result
