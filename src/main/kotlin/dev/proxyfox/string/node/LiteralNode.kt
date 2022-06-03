package dev.proxyfox.string.node

import dev.proxyfox.string.parser.MessageHolder

class LiteralNode(private val literal: String, val executor: suspend MessageHolder.() -> String) : Node {
    private val literalNodes: ArrayList<LiteralNode> = ArrayList()
    private val stringNodes: ArrayList<StringNode> = ArrayList()
    private val greedyNodes: ArrayList<GreedyNode> = ArrayList()

    override fun parse(string: String, index: Int, holder: MessageHolder): Int {
        if (string.length < literal.length + index) return index
        if (string.substring(index).lowercase().startsWith(literal.lowercase()))
            return index + literal.length
        return index
    }

    override fun getSubNodes(): Array<Node> {
        val literalArray: Array<Node> = literalNodes.toTypedArray()
        val stringArray: Array<Node> = stringNodes.toTypedArray()
        val greedyArray: Array<Node> = greedyNodes.toTypedArray()
        return literalArray + stringArray + greedyArray
    }

    override fun addSubNode(node: Node) {
        when (node) {
            is LiteralNode -> literalNodes.add(node)
            is StringNode -> stringNodes.add(node)
            is GreedyNode -> greedyNodes.add(node)
        }
    }

    override suspend fun execute(holder: MessageHolder) = executor(holder)
}