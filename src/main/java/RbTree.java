import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author xiaokang
 * @since 2020-05-04
 */
public class RbTree<T extends Comparable<T>> {
    private final RbTreeNode<T> root;

    /**
     * 使用AtomicLong能让long的操作保持原子型
     */
    private final AtomicLong size = new AtomicLong(0);

    /**
     * 覆盖模式：开启后，一个值只能出现一次，相同便覆盖。
     */
    private volatile boolean overrideMode = true;

    public RbTree() {
        this.root = new RbTreeNode<>();
    }

    public RbTree(boolean overrideMode) {
        this();
        this.overrideMode = overrideMode;
    }

    public boolean isOverrideMode() {
        return overrideMode;
    }

    public void setOverrideMode(boolean overrideMode) {
        this.overrideMode = overrideMode;
    }

    public long getSize() {
        return size.get();
    }


    /**
     * 假设root的左节点为整个红黑树的根节点
     */
    private RbTreeNode<T> getRoot() {
        return root.getLeft();
    }

    /**
     * 查找节点
     */
    public T find(T value) {
        RbTreeNode<T> dataRoot = getRoot();
        while (dataRoot != null) {
            int cmp = dataRoot.getValue().compareTo(value);
            if (cmp < 0) {
                dataRoot = dataRoot.getRight();
            } else if (cmp > 0) {
                dataRoot = dataRoot.getLeft();
            } else {
                return dataRoot.getValue();
            }
        }
        return null;
    }

    /**
     * 添加节点
     */
    public T addNode(T value) {
        RbTreeNode<T> t = new RbTreeNode<>(value);
        return addNode(t);
    }

    /**
     * 添加节点的实现
     */
    private T addNode(RbTreeNode<T> node) {
        // 初始化节点
        node.setLeft(null);
        node.setRight(null);
        node.setRed(true);
        setParent(node, null);

        // 如果根节点为空，则<insert节点>直接成为根节点
        if (getRoot() == null) {
            root.setLeft(node);
            node.setRed(false);
        } else {
            // 获取对应的<父节点>
            RbTreeNode<T> x = findParent(node);
            int cmp = x.getValue().compareTo(node.getValue());
            // 覆盖开启且值相等，则覆盖并返回
            if (this.overrideMode && cmp == 0) {
                T v = x.getValue();
                x.setValue(node.getValue());
                return v;
            } else if (cmp == 0) {
                // 覆盖不开启，值相等则忽略该节点
                return x.getValue();
            }
            // 值不相等，则<insert节点>与<父节点>互相链接
            setParent(node, x);
            if (cmp > 0) {
                x.setLeft(node);
            } else {
                x.setRight(node);
            }
            // 开始调整红黑树
            fixInsert(node);
        }
        size.incrementAndGet();
        return null;
    }

    /**
     * 插入节点完成后，修复红黑树（最多两次旋转达到平衡）
     */
    private void fixInsert(RbTreeNode<T> x) {
        RbTreeNode<T> parent = x.getParent();
        // 只有在<父节点>为红色的时候需要修复
        while (parent != null && parent.isRed()) {
            RbTreeNode<T> uncle = getUncle(x);
            // case1，<叔叔节点>存在且为红色
            if (uncle != null && uncle.isRed()) {
                // 将<叔叔节点>和<父节点>均变黑，<祖父节点>变红
                parent.setRed(false);
                uncle.setRed(false);
                parent.getParent().setRed(true);
                // 变色后需要检查<祖父节点>与<曾祖父节点>是否满足规则（可能存在双红，则继续向上回溯）
                x = parent.getParent();
                parent = x.getParent();
            } else {
                RbTreeNode<T> ancestor = parent.getParent();
                // <父节点>在<祖父节点>左边
                if (parent == ancestor.getLeft()) {
                    // 判断<insert节点>是否在<父节点>左边
                    boolean isRight = x == parent.getRight();
                    // 如果不是，则符合case3，需要<父节点>左旋形成case2
                    if (isRight) {
                        rotateLeft(parent);
                    }
                    // 此时符合case2（<insert节点><父节点><祖父节点>三子同侧），<祖父节点>右旋
                    rotateRight(ancestor);
                    /*
                     * 旋转完成后进行变色
                     * case3：子上->变黑，父下->叶子结点
                     * case2：父上->变黑，子下-不变
                     */
                    if (isRight) {
                        x.setRed(false);
                        // 循环结束
                        parent = null;
                    } else {
                        parent.setRed(false);
                    }
                    // 不管是case2或case3，最后<祖父节点>都要变成红色
                    ancestor.setRed(true);
                }
                // <父节点>在<祖父节点>右边（镜像情况）
                else {
                    boolean isLeft = x == parent.getLeft();
                    if (isLeft) {
                        rotateRight(parent);
                    }
                    rotateLeft(ancestor);
                    if (isLeft) {
                        x.setRed(false);
                        parent = null;
                    } else {
                        parent.setRed(false);
                    }
                    ancestor.setRed(true);
                }
            }
        }

        // 最后根节点变黑
        getRoot().makeBlack();
        getRoot().setParent(null);
    }

    /**
     * 删除节点
     */
    public T remove(T value) {
        // 待删除的节点
        RbTreeNode<T> dataRoot = getRoot();
        // 待删除节点的父节点
        RbTreeNode<T> parent = root;

        while (dataRoot != null) {
            // 从根节点开始查找value值（此处巧妙在于，dataRoot与parent交互赋值，始终保持子父关系）
            int cmp = dataRoot.getValue().compareTo(value);
            if (cmp < 0) {
                parent = dataRoot;
                dataRoot = dataRoot.getRight();
            } else if (cmp > 0) {
                parent = dataRoot;
                dataRoot = dataRoot.getLeft();
            }
            // 如果value值相等，则定位到<delete节点>
            else {
                /*
                 * delete节点有三种情况：2个子节点、1个子节点、没有子节点
                 * 有2个子节点：可以通过用后继节点顶替，从而转为叶子节点的情形
                 * 有1个子节点：必为黑色，因为红色节点不可能只有一个节点，否则违反规则4
                 * 叶子节点：可以直接删除 -> 若为黑色则需要修复
                 *
                 * 归纳为两类则是：
                 * 第一类：有右节点（一左一右，只有右）-> 用后继节点顶替 -> 若为黑色则需要修复
                 * 第二类：只有左节点 -> 直接用左节点顶替 -> 需要修复
                 *        或没有节点（叶子节点）-> 直接删除 -> 若为黑色则需要修复
                 */
                // 第一类：有右节点
                if (dataRoot.getRight() != null) {
                    // 获取<后继节点>（即要顶替<delete节点>）
                    RbTreeNode<T> min = removeMin(dataRoot);
                    // 此处需要修复的节点，要么为<父节点>，要么为<右子节点>
                    RbTreeNode<T> x = min.getRight() == null ? min.getParent() : min.getRight();
                    boolean isParent = min.getRight() == null;
                    // 带删除的节点颜色
                    boolean curMinIsBlack = min.isBlack();

                    // -------开始重组后继节点（取代dataRoot）-------
                    // 重组<左节点>
                    min.setLeft(dataRoot.getLeft());
                    setParent(dataRoot.getLeft(), min);
                    // 重组<父节点>
                    if (parent.getLeft() == dataRoot) {
                        // 如果dataRoot为根节点，则min升级为根节点
                        parent.setLeft(min);
                    } else {
                        parent.setRight(min);
                    }
                    setParent(min, parent);
                    // 重组<右节点>
                    if (min != dataRoot.getRight()) {
                        min.setRight(dataRoot.getRight());
                        setParent(dataRoot.getRight(), min);
                    }
                    // 继承颜色
                    min.setRed(dataRoot.isRed());
                    // ---------------重组后继节点结束---------------

                    // 如果<后继节点>的颜色为黑色，则需要修复
                    if (curMinIsBlack) {
                        if (min != dataRoot.getRight()) {
                            // <后继节点>与<delete节点>不是父子关系
                            fixRemove(x, isParent);
                        } else if (min.getRight() != null) {
                            // <后继节点>与<delete节点>是父子关系，且<后继节点>有<右子节点>
                            fixRemove(min.getRight(), false);
                        } else {
                            // <后继节点>与<delete节点>是父子关系，且<后继节点>只有<左子节点>
                            fixRemove(min, true);
                        }
                    }
                }
                // 第二类：只有左节点或没有节点（叶子节点）
                else {
                    // 将<delete节点>的<左子节点>与<delete节点>的<父节点>互相链接（包含叶子节点）
                    setParent(dataRoot.getLeft(), parent);
                    if (parent.getLeft() == dataRoot) {
                        parent.setLeft(dataRoot.getLeft());
                    } else {
                        parent.setRight(dataRoot.getLeft());
                    }
                    // 如果<delete节点>是黑色（包含叶子节点）并且树不为空，则需要修复。
                    if (dataRoot.isBlack() && getRoot() != null) {
                        // 此处需要修复的节点，要么为<父节点>，要么为<左子节点>
                        RbTreeNode<T> x = dataRoot.getLeft() == null ? parent : dataRoot.getLeft();
                        boolean isParent = dataRoot.getLeft() == null;
                        fixRemove(x, isParent);
                    }
                }
                // 修复完成后，原待删除的节点清空链接
                setParent(dataRoot, null);
                dataRoot.setLeft(null);
                dataRoot.setRight(null);
                // 修复完成后，确保根节点为黑色
                if (getRoot() != null) {
                    getRoot().setRed(false);
                    getRoot().setParent(null);
                }
                // 节点数量减1
                size.decrementAndGet();
                return dataRoot.getValue();
            }
        }
        return null;
    }

    /**
     * 删除节点完成后，修复红黑树（最多三次旋转达到平衡）
     */
    private void fixRemove(RbTreeNode<T> node, boolean isParent) {
        //（局部）当前节点
        RbTreeNode<T> cur = isParent ? null : node;
        //（局部）当前节点是否为红色
        boolean isRed = !isParent && node.isRed();
        //（局部）当前节点的父节点
        RbTreeNode<T> parent = isParent ? node : node.getParent();

        // <当前节点>为黑色，且不为根节点（向上回溯，直至根节点）
        while (!isRed && !isRoot(cur)) {
            // 获取<当前节点>的<兄弟节点>
            RbTreeNode<T> sibling = getSibling(cur, parent);
            // 判断<当前节点>是否在左边
            boolean isLeft = parent.getRight() == sibling;

            /*
             * case1：<兄弟节点>为红色（此时根据规则3，<侄子节点>必均为黑，通过旋转，从<侄子节点>借调黑节点）
             */
            if (sibling.isRed() && isLeft) {
                // <当前节点>在左边，<父节点>变红，<兄弟节点>变黑，<父节点>左旋
                parent.makeRed();
                sibling.makeBlack();
                rotateLeft(parent);
            } else if (sibling.isRed() && !isLeft) {
                // <当前节点>在右边，<父节点>变红，<兄弟节点>变黑，<父节点>右旋
                parent.makeRed();
                sibling.makeBlack();
                rotateRight(parent);
            }
            /*
             * case2：<兄弟节点>为黑色，且<侄子节点>都为黑色（此时不能从<兄弟节点>或<侄子节点>借调，否则违反规则4）
             */
            else if (isBlack(sibling.getLeft()) && isBlack(sibling.getRight())) {
                // <兄弟节点>变红
                sibling.makeRed();
                // <父节点>转为<当前节点>
                cur = parent;
                // <当前节点>的颜色作为循环开关
                isRed = cur.isRed();
                // <祖父节点>转为<父节点>，向上回溯
                parent = parent.getParent();
            }
            /*
             * case3：<兄弟节点>为黑色，且<侄子节点>为"左红右黑"（需要将红节点借调到父、兄、侄三点同侧，从而转换为case4）
             */
            else if (isLeft && !isBlack(sibling.getLeft()) && isBlack(sibling.getRight())) {
                // <当前节点>在左边，<侄子节点>为"左红右黑"，<兄弟节点>变红，<左侄子节点>变黑，<兄弟节点>右旋
                sibling.makeRed();
                sibling.getLeft().makeBlack();
                rotateRight(sibling);
            } else if (!isLeft && isBlack(sibling.getLeft()) && !isBlack(sibling.getRight())) {
                // <当前节点>在右边，<侄子节点>为"左黑右红"，<兄弟节点>变红，<右侄子节点>变黑，<兄弟节点>左旋（镜像情况）
                sibling.makeRed();
                sibling.getRight().makeBlack();
                rotateLeft(sibling);
            }
            /*
             * case4：<兄弟节点>为黑色，且<右侄子节点>为红色
             * （不需要关心<左侄子节点>，只要父、兄、侄三点同侧即可，因为需要借调<兄弟节点>-黑、<右侄子节点>-红）
             */
            else if (isLeft && !isBlack(sibling.getRight())) {
                // <兄弟节点>继承<父节点>的颜色，<父节点>变黑，<右侄子节点>变黑
                sibling.setRed(parent.isRed());
                parent.makeBlack();
                sibling.getRight().makeBlack();
                // <父节点>左旋，则修复完成
                rotateLeft(parent);
                // 循环结束
                cur = getRoot();
            } else if (!isLeft && !isBlack(sibling.getLeft())) {
                // <兄弟节点>继承<父节点>的颜色，<父节点>变黑，<左侄子节点>变黑
                sibling.setRed(parent.isRed());
                parent.makeBlack();
                sibling.getLeft().makeBlack();
                // <父节点>左旋，则修复完成
                rotateRight(parent);
                // 循环结束
                cur = getRoot();
            }
        }
        // 如果<当前节点>为红色，则将其变黑
        if (isRed) {
            cur.makeBlack();
        }
        // 修复完成后，确保根节点为黑色
        if (getRoot() != null) {
            getRoot().setRed(false);
            getRoot().setParent(null);
        }
    }

    /**
     * 查找父节点
     */
    private RbTreeNode<T> findParent(RbTreeNode<T> x) {
        RbTreeNode<T> dataRoot = getRoot();
        RbTreeNode<T> child = dataRoot;
        // 查找父节点是个循环的过程，这里用不断更换child和dataRoot所指的对象来实现追根溯源。
        // 没有用递归，是因为递归太占资源。
        while (child != null) {
            int cmp = child.getValue().compareTo(x.getValue());
            if (cmp == 0) {
                return child;
            }
            dataRoot = child;
            if (cmp > 0) {
                child = child.getLeft();
            } else {
                child = child.getRight();
            }
        }
        return dataRoot;
    }

    /**
     * 设置新的父节点
     */
    private void setParent(RbTreeNode<T> node, RbTreeNode<T> parent) {
        if (node != null) {
            node.setParent(parent);
            if (parent == root) {
                node.setParent(null);
            }
        }
    }

    /**
     * 查找兄弟节点
     */
    private RbTreeNode<T> getSibling(RbTreeNode<T> node, RbTreeNode<T> parent) {
        parent = node == null ? parent : node.getParent();
        if (node == null) {
            return parent.getLeft() == null ? parent.getRight() : parent.getLeft();
        }
        if (node == parent.getLeft()) {
            return parent.getRight();
        } else {
            return parent.getLeft();
        }
    }

    /**
     * 查找叔叔节点
     */
    private RbTreeNode<T> getUncle(RbTreeNode<T> node) {
        RbTreeNode<T> parent = node.getParent();
        RbTreeNode<T> ancestor = parent.getParent();
        if (ancestor == null) {
            return null;
        }
        if (parent == ancestor.getLeft()) {
            return ancestor.getRight();
        } else {
            return ancestor.getLeft();
        }
    }

    /**
     * 查找后继节点
     */
    private RbTreeNode<T> removeMin(RbTreeNode<T> node) {
        // 按照后继节点定义，先取node的右节点 nodeRight
        RbTreeNode<T> nodeRight = node.getRight();
        RbTreeNode<T> parent = nodeRight;
        // 然后一直取 nodeRight的左节点，直到为空
        while (nodeRight != null && nodeRight.getLeft() != null) {
            parent = nodeRight;
            nodeRight = nodeRight.getLeft();
        }
        // 如果nodeRight没有左节点，则nodeRight作为后继节点直接返回
        if (parent == nodeRight) {
            return nodeRight;
        }
        // 此时nodeRight即为后继节点（已经没有左节点了，只可能有右节点）
        // 返回之前先将<后继节点>的<右节点>与<父节点>互相链接
        parent.setLeft(nodeRight.getRight());
        setParent(nodeRight.getRight(), parent);

        // 此处不能删除右节点，因为修复颜色平衡时需要用到
        //nodeRight.setRight(null);

        return nodeRight;
    }

    /**
     * 判断节点颜色是否为黑色
     */
    private boolean isBlack(RbTreeNode<T> node) {
        return node == null || node.isBlack();
    }

    /**
     * 判断节点是否为根节点
     */
    private boolean isRoot(RbTreeNode<T> node) {
        return getRoot() == node && node.getParent() == null;
    }

    /**
     * 左旋
     */
    private void rotateLeft(RbTreeNode<T> node) {
        RbTreeNode<T> right = node.getRight();
        if (right == null) {
            throw new java.lang.IllegalStateException("right node is null");
        }
        RbTreeNode<T> parent = node.getParent();
        // node节点 <=> right的左节点
        node.setRight(right.getLeft());
        setParent(right.getLeft(), node);
        // node节点 <=> right节点
        right.setLeft(node);
        setParent(node, right);
        // right节点 <=> parent节点
        if (parent == null) {
            // 如果parent节点为null，则right节点升至根节点
            root.setLeft(right);
            setParent(right, null);
        } else {
            // 如果parent节点不为null，则将right链接到原node相对parent的位置
            // 即node在parent左边，则right链接到node左边，反之亦然
            if (parent.getLeft() == node) {
                parent.setLeft(right);
            } else {
                parent.setRight(right);
            }
            setParent(right, parent);
        }
        System.out.println("【" + node.getValue() + "】节点左旋");
    }

    /**
     * 右旋
     */
    private void rotateRight(RbTreeNode<T> node) {
        RbTreeNode<T> left = node.getLeft();
        if (left == null) {
            throw new java.lang.IllegalStateException("left node is null");
        }
        RbTreeNode<T> parent = node.getParent();
        node.setLeft(left.getRight());
        setParent(left.getRight(), node);
        // 与左旋同理
        left.setRight(node);
        setParent(node, left);
        if (parent == null) {
            root.setLeft(left);
            setParent(left, null);
        } else {
            if (parent.getLeft() == node) {
                parent.setLeft(left);
            } else {
                parent.setRight(left);
            }
            setParent(left, parent);
        }
        System.out.println("【" + node.getValue() + "】节点右旋");
    }

    /**
     * 打印红黑树
     */
    public void printRbTree(RbTreeNode<T> root) {
        if (root == null) {
            return;
        }

        LinkedList<RbTreeNode<T>> queue = new LinkedList<>();
        LinkedList<RbTreeNode<T>> queue2 = new LinkedList<>();
        queue.add(root);
        boolean firstQueue = true;
        while (!queue.isEmpty() || !queue2.isEmpty()) {
            LinkedList<RbTreeNode<T>> q = firstQueue ? queue : queue2;
            RbTreeNode<T> n = q.poll();
            if (n != null) {
                String pos = n.getParent() == null ? "" : (n == n.getParent().getLeft() ? " LE" : " RI");
                String pstr = n.getParent() == null ? "" : n.getParent().toString();
                String cstr = n.isRed() ? "R" : "B";
                cstr = n.getParent() == null ? cstr : cstr + " ";
                System.out.print(n + "(" + (cstr) + pstr + (pos) + ")" + "\t");
                if (n.getLeft() != null) {
                    (firstQueue ? queue2 : queue).add(n.getLeft());
                }
                if (n.getRight() != null) {
                    (firstQueue ? queue2 : queue).add(n.getRight());
                }
            } else {
                System.out.println();
                firstQueue = !firstQueue;
            }
        }
    }

    public static void main(String[] args) {
        RbTree<Integer> bst = new RbTree<>();
        Integer[] array = {12, 1, 9, 2, 0, 11, 7, 19, 4, 15, 18, 5, 14, 13, 10, 16, 6, 3, 8, 17};
        for (Integer i : array) {
            bst.addNode(i);
        }
        System.out.println("==============原红黑树==============");
        bst.printRbTree(bst.getRoot());
        System.out.println();

        // 删除
        System.out.println("==============删除后的红黑树==============");
        bst.remove(12);
        bst.printRbTree(bst.getRoot());
    }
}

/**
 * 红黑树节点类
 */
class RbTreeNode<T extends Comparable<T>> {
    /**
     * 节点值
     */
    private T value;
    /**
     * 左子树
     */
    private RbTreeNode<T> left;
    /**
     * 右子树
     */
    private RbTreeNode<T> right;
    /**
     * 父节点
     */
    private RbTreeNode<T> parent;
    /**
     * 节点颜色（红与非红）
     */
    private boolean red;

    public RbTreeNode() {
    }

    public RbTreeNode(T value) {
        this.value = value;
    }

    public RbTreeNode(T value, boolean isRed) {
        this.value = value;
        this.red = isRed;
    }

    public T getValue() {
        return value;
    }

    void setValue(T value) {
        this.value = value;
    }

    RbTreeNode<T> getLeft() {
        return left;
    }

    void setLeft(RbTreeNode<T> left) {
        this.left = left;
    }

    RbTreeNode<T> getRight() {
        return right;
    }

    void setRight(RbTreeNode<T> right) {
        this.right = right;
    }

    RbTreeNode<T> getParent() {
        return parent;
    }

    void setParent(RbTreeNode<T> parent) {
        this.parent = parent;
    }

    boolean isRed() {
        return red;
    }

    boolean isBlack() {
        return !red;
    }

    boolean isLeaf() {
        return left == null && right == null;
    }

    void setRed(boolean red) {
        this.red = red;
    }

    void makeRed() {
        red = true;
    }

    void makeBlack() {
        red = false;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}