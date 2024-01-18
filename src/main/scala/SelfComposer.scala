import de.fosd.typechef.parser.c.PrettyPrinter.printW
import de.fosd.typechef.parser.c.{AST, TranslationUnit}

import scala.sys.process.*
import java.io.{BufferedWriter, ByteArrayInputStream, File, FileWriter}
import java.nio.file.{Path, Paths}

object SelfComposer {
  def apply(oldTranslationUnit: TranslationUnit,
            newTranslationUnit: TranslationUnit,
            functionName: String,
            out: File,
            typechefArgs: Array[String]): TranslationUnit = {

    val oldFilepath = Paths.get(out.toString, "old.c")
    val newFilepath = Paths.get(out.toString, "new.c")
    val outFilepath = Paths.get(out.toString, "0.c")
    
    CodeGenerator(oldTranslationUnit, oldFilepath)
    CodeGenerator(newTranslationUnit, newFilepath)

    ("python3 -" #< new ByteArrayInputStream(
      s"""
         |import argparse
         |import copy
         |
         |import pycparser_fake_libc
         |from pycparser import parse_file, c_generator, c_ast
         |
         |Return_bindings = {}
         |impacted = False
         |renamed = set()
         |declared = set()
         |old_touched = set()
         |new_touched = set()
         |touched = set()
         |
         |
         |def clear_global():
         |    global impacted
         |    global old_touched
         |    global new_touched
         |    global touched
         |    global renamed
         |    global declared
         |    global value_copied
         |    impacted = False
         |    old_touched = set()
         |    new_touched = set()
         |    touched = set()
         |    renamed = set()
         |    declared = set()
         |    value_copied = set()
         |    return
         |
         |
         |class FuncDefVisitor(c_ast.NodeVisitor):
         |    def __init__(self, target, prefix=False):
         |        self.target = target
         |        self.container = None
         |        self.prefix = prefix
         |
         |    def visit_FuncDef(self, node):
         |        if (self.container is None and node.decl.name == self.target):
         |            self.container = node
         |            return
         |        elif (self.prefix and (node.decl.name == self.target + "_old" or node.decl.name == self.target + "_new")):
         |            self.container = node
         |            return
         |
         |
         |class DateTypeVisitor(c_ast.NodeVisitor):
         |
         |    def __init__(self):
         |        self.type_dict = {}
         |
         |    def visit_Decl(self, node):
         |        if isinstance(node, c_ast.Decl):
         |            type = node.type
         |            while (type is not None):
         |                if isinstance(type, c_ast.FuncDecl):
         |                    if type.args is not None:
         |                        self.visit(type.args)
         |                if isinstance(type, c_ast.IdentifierType):
         |                    self.type_dict[node.name] = type.names
         |                    break
         |                else:
         |                    try:
         |                        type = type.type
         |                    except:
         |                        break
         |
         |
         |class ReturnHuntVisitor(c_ast.NodeVisitor):
         |    _method_cache = None
         |
         |    def __init__(self, return_type, return_name, single_return=False):
         |        self.return_type = return_type
         |        self.return_name = return_name
         |        self.return_nums = 0
         |        self.single_return = single_return
         |        self.assignment_bindings = {}
         |
         |    def visit_Return(self, node, parent, index=0):
         |        if (self.single_return):
         |            assignment = c_ast.Assignment(lvalue=c_ast.ID(self.return_name.format(0)),
         |                                          rvalue=node.expr, op="=")
         |        else:
         |            assignment = c_ast.Assignment(lvalue=c_ast.ID(self.return_name.format(self.return_nums)),
         |                                          rvalue=node.expr, op="=")
         |            self.assignment_bindings[self.return_name.format(self.return_nums)] = node.expr
         |        if isinstance(parent, c_ast.Compound):
         |            parent.block_items[index] = assignment
         |        elif isinstance(parent, c_ast.If):
         |            if (index == 1):
         |                parent.iftrue = assignment
         |            elif (index == 2):
         |                parent.iffalse = assignment
         |        elif isinstance(parent, c_ast.For):
         |            parent.stmt = assignment
         |        elif isinstance(parent, c_ast.While):
         |            parent.stmt = assignment
         |        self.return_nums += 1
         |
         |    def visit(self, node, parent, index=0):
         |        \"\"\" Visit a node.
         |        \"\"\"
         |        if self._method_cache is None:
         |            self._method_cache = {}
         |
         |        visitor = self._method_cache.get(node.__class__.__name__, None)
         |        if visitor is None:
         |            method = 'visit_' + node.__class__.__name__
         |            visitor = getattr(self, method, self.generic_visit)
         |            self._method_cache[node.__class__.__name__] = visitor
         |
         |        return visitor(node, parent, index)
         |
         |    def generic_visit(self, node, parent, index=0):
         |        \"\"\" Called if no explicit visitor function exists for a
         |            node. Implements preorder visiting of the node.
         |        \"\"\"
         |        counter = 0
         |        for c in node:
         |            self.visit(c, node, counter)
         |            counter += 1
         |
         |
         |def constant_zero():
         |    return c_ast.Constant(value='0', type='int')
         |
         |
         |def get_type(function_node):
         |    if isinstance(function_node, c_ast.FuncDef):
         |        fun_type = function_node.decl.type
         |    else:
         |        fun_type = function_node.type
         |    while not isinstance(fun_type, c_ast.IdentifierType):
         |        fun_type = fun_type.type
         |    return copy.deepcopy(fun_type.names)
         |
         |
         |class IDhunterRaw(c_ast.NodeVisitor):
         |    def __init__(self):
         |        self.container = []
         |
         |    def visit_ID(self, node):
         |        if isinstance(node, c_ast.ID):
         |            self.container.append(node)
         |
         |
         |class IDRenameVisitor(c_ast.NodeVisitor):
         |    \"\"\"
         |    Version and Rename touched variables
         |    \"\"\"
         |
         |    def __init__(self, version, targets):
         |        self.version = version
         |        self.targets = targets
         |        self.renamed = set()
         |        self.declared = set()
         |
         |    def visit_ID(self, node):
         |        if isinstance(node, c_ast.ID):
         |            if not node.name.endswith("_" + self.version) and node.name in self.targets:
         |                self.renamed.add((node.name, self.version))
         |                node.name = (node.name + "_" + self.version)
         |
         |    def visit_Decl(self, node):
         |        if isinstance(node, c_ast.Decl):
         |            if node.name and not node.name.endswith("_" + self.version) and node.name in self.targets:
         |                self.declared.add((node.name, self.version))
         |                node.name = (node.name + "_" + self.version)
         |            if isinstance(node.type, c_ast.TypeDecl):
         |                if not node.type.declname.endswith("_" + self.version) and node.type.declname in self.targets:
         |                    self.declared.add((node.name, self.version))
         |                    node.type.declname = (node.type.declname + "_" + self.version)
         |            if node.init is not None:
         |                hunter = IDhunterRaw()
         |                hunter.visit(node.init)
         |                for tnode in hunter.container:
         |                    if not tnode.name.endswith("_" + self.version) and tnode.name in self.targets:
         |                        self.renamed.add((tnode.name, self.version))
         |                        tnode.name = (tnode.name + "_" + self.version)
         |
         |
         |def rename_ID(old, new, touched_set):
         |    global renamed
         |    global declared
         |    if (len(touched_set) > 0):
         |        renamer = IDRenameVisitor("", list(touched_set))
         |        if (old is not None):
         |            renamer.version = "old"
         |            renamer.visit(old)
         |        if (new is not None):
         |            renamer.version = "new"
         |            renamer.visit(new)
         |        renamed = renamer.renamed.union(renamed)
         |        declared = renamer.declared.union(declared)
         |
         |
         |class DataModVisitor(c_ast.NodeVisitor):
         |    def __init__(self):
         |        self.define = set()
         |        self.use = set()
         |
         |    def visit_Assignment(self, node):
         |        if isinstance(node, c_ast.Assignment):
         |            if node.lvalue is not None:
         |                if isinstance(node.lvalue, c_ast.ID):
         |                    self.define.add(node.lvalue.name)
         |                else:
         |                    self.visit(node.lvalue)
         |            if node.rvalue is not None:
         |                if isinstance(node.rvalue, c_ast.ID):
         |                    self.use.add(node.rvalue.name)
         |                else:
         |                    self.visit(node.rvalue)
         |
         |    def visit_UnaryOp(self, node):
         |        if isinstance(node, c_ast.UnaryOp):
         |            if (node.op.endswith("--") or node.op.endswith("++")):
         |                if isinstance(node.expr, c_ast.ID):
         |                    self.define.add(node.expr.name)
         |                    self.use.add(node.expr.name)
         |                else:
         |                    self.visit(node.expr)
         |            elif node.expr is not None:
         |                if isinstance(node.expr, c_ast.ID):
         |                    self.use.add(node.expr.name)
         |                else:
         |                    self.visit(node.expr)
         |
         |    def visit_Decl(self, node):
         |        if isinstance(node, c_ast.Decl):
         |            self.define.add(node.name)
         |
         |            if node.init is not None:
         |                if isinstance(node.init, c_ast.ID):
         |                    self.use.add(node.init.name)
         |                else:
         |                    self.visit(node.init)
         |
         |    def visit_BinaryOp(self, node):
         |        if isinstance(node, c_ast.BinaryOp):
         |            if node.left is not None:
         |                if isinstance(node.left, c_ast.ID):
         |                    self.use.add(node.left)
         |                else:
         |                    self.visit(node.left)
         |            if node.right is not None:
         |                if isinstance(node.right, c_ast.ID):
         |                    self.use.add(node.right)
         |                else:
         |                    self.visit(node.right)
         |
         |
         |def mark_touched_variables(old, new, ignore_id=False):
         |    global impacted
         |    global old_touched
         |    global new_touched
         |    global touched
         |
         |    dv_old = DataModVisitor()
         |    if old is not None:
         |        dv_old.visit(old)
         |    dv_new = DataModVisitor()
         |    if new is not None:
         |        dv_new.visit(new)
         |
         |    if (str(new) != str(old) or ignore_id):
         |        old_touched = old_touched.union(dv_old.define)
         |        new_touched = new_touched.union(dv_new.define)
         |
         |    # now look at the use set. use set is considered if it has an intesection with define set.
         |    old_touched = old_touched.union(dv_old.use.intersection(new_touched))
         |    new_touched = new_touched.union(dv_new.use.intersection(old_touched))
         |
         |    new_int = old_touched.union(new_touched)
         |    set_of_interest = new_int.difference(touched)
         |    touched = new_int
         |    return set_of_interest
         |
         |
         |def merge_expressions(old, new):
         |    rename_ID(old.next, new.next, touched)
         |    if str(new.next) == str(old.next):
         |        return new.next
         |    if isinstance(new.next, c_ast.ExprList):
         |        exprs = new.next.exprs
         |    else:
         |        exprs = [new.next]
         |
         |    if isinstance(old.next, c_ast.ExprList):
         |        exprs += old.next.exprs
         |    else:
         |        exprs.append(old.next)
         |
         |    return c_ast.ExprList(exprs)
         |
         |
         |def add_nodes (node_list, new_node):
         |    if isinstance(new_node, list):
         |        node_list+=new_node
         |    else:
         |        node_list.append(new_node)
         |
         |
         |def list_merge(old_list, new_list, old_start, new_start, old_limit, new_limit, target):
         |    while (old_start < old_limit or new_start < new_limit):
         |        if (old_start < old_limit and new_start < new_limit):
         |            add_nodes(target, merge(old_list[old_start], new_list[new_start], well_formed=False))
         |            old_start += 1
         |            new_start += 1
         |        elif old_start < old_limit:
         |            target.append(merge(old_list[old_start], None))
         |            old_start += 1
         |        elif new_start < new_limit:
         |            target.append(merge(None, new_list[new_start]))
         |            new_start += 1
         |
         |
         |def merge(old, new, well_formed=True):
         |    global touched
         |    rename_ID(old, new, touched)
         |    # case 1, new and old are syntactically identical
         |    if (str(new) == str(old)):
         |        new_touched = mark_touched_variables(old, new)
         |        rename_ID(old, new, touched)
         |        if (str(new) == str(old)):
         |            return new
         |    # case 2, if one side is empty:
         |    elif old is None:
         |        new_touched = mark_touched_variables(None, new)
         |        rename_ID(old, new, touched)
         |        return new
         |    elif new is None:
         |        new_touched = mark_touched_variables(old, None)
         |        rename_ID(old, new, touched)
         |        return old
         |    # case 3, new or old are compound block
         |    if isinstance(new, c_ast.Compound) or isinstance(new, c_ast.Compound):
         |        if (new, c_ast.Compound):
         |            new_blocks = new.block_items
         |        else:
         |            new_blocks = [new]
         |        if isinstance(old, c_ast.Compound):
         |            old_blocks = old.block_items
         |        else:
         |            old_blocks = [old]
         |        merged = []
         |
         |        # try to find loop block
         |        old_loop_node_index = []
         |        new_loop_node_index = []
         |
         |        if old_blocks is not None:
         |            for i in range(len(old_blocks)):
         |                block = old_blocks[i]
         |                if (isinstance(block, c_ast.For) or
         |                        isinstance(block, c_ast.While) or
         |                        isinstance(block, c_ast.DoWhile)):
         |                    old_loop_node_index.append(i)
         |
         |        if new_blocks is not None:
         |            for i in range(len(new_blocks)):
         |                block = new_blocks[i]
         |                if (isinstance(block, c_ast.For) or
         |                        isinstance(block, c_ast.While) or
         |                        isinstance(block, c_ast.DoWhile)):
         |                    new_loop_node_index.append(i)
         |
         |        cur_old_i = 0
         |        cur_new_i = 0
         |        merging_loop_index = 0
         |        while (merging_loop_index < min(len(new_loop_node_index), len(old_loop_node_index))):
         |            cur_old_loop_i = old_loop_node_index[merging_loop_index]
         |            cur_new_loop_i = new_loop_node_index[merging_loop_index]
         |
         |            list_merge(old_blocks, new_blocks, cur_old_i, cur_new_i, cur_old_loop_i, cur_new_loop_i, merged)
         |
         |            add_nodes(merged, merge(old_blocks[cur_old_loop_i], new_blocks[cur_new_loop_i], well_formed=False))
         |            cur_old_i = cur_old_loop_i + 1
         |            cur_new_i = cur_new_loop_i + 1
         |            merging_loop_index += 1
         |
         |        list_merge(old_blocks, new_blocks, cur_old_i, cur_new_i,
         |                   0 if old_blocks is None else len(old_blocks),
         |                   0 if new_blocks is None else len(new_blocks), merged)
         |
         |        return c_ast.Compound(merged)
         |
         |
         |    # case 4, if the two nodes disagree in types
         |    elif type(new) != type(old):
         |        touched_set = mark_touched_variables(old, new)
         |        if (len(touched_set) > 0):
         |            rename_ID(old, new, list(touched_set))
         |        if (well_formed):
         |            return c_ast.Compound([old, new])
         |        else:
         |            return [old, new]
         |
         |    # case 6, merge decls
         |    elif isinstance(new, c_ast.DeclList) and isinstance(old, c_ast.DeclList):
         |        return c_ast.DeclList(decls=(list(set(new.decls + old.decls))))
         |
         |    # case 5, merge for loops
         |    elif isinstance(new, c_ast.For) and isinstance(old, c_ast.For):
         |        # ltry to find a variable version fix-point
         |        while (True):
         |            initals = merge(new.init, old.init)
         |            mark_touched_variables(old.cond, new.cond)
         |            rename_ID(old.cond, new.cond, touched)
         |            disjunct_for_cond = c_ast.BinaryOp(left=old.cond, right=new.cond, op='||')
         |            old_statement = c_ast.If(cond=old.cond, iftrue=old.stmt, iffalse=None)
         |            new_statement = c_ast.If(cond=new.cond, iftrue=new.stmt, iffalse=None)
         |            touched_old_size = len(touched)
         |            loop_body = c_ast.Compound([merge(old_statement, new_statement)])
         |            if (str(old.cond) != str(new.cond)):
         |                mark_touched_variables(old.next, new.next, ignore_id=True)
         |            merged_exp = merge_expressions(old, new)
         |            if (len(touched) == touched_old_size):
         |                break
         |
         |        merged_for = c_ast.For(init=initals, next=merged_exp, cond=disjunct_for_cond, stmt=loop_body)
         |
         |        return merged_for
         |
         |    # case 6, merge while loops
         |    elif isinstance(new, c_ast.While) and isinstance(old, c_ast.While):
         |        # ltry to find a variable version fix-point
         |        while (True):
         |            mark_touched_variables(old.cond, new.cond)
         |            rename_ID(old.cond, new.cond, touched)
         |            disjunct_while_cond = c_ast.BinaryOp(left=old.cond, right=new.cond, op='||')
         |            old_statement = c_ast.If(cond=old.cond, iftrue=old.stmt, iffalse=None)
         |            new_statement = c_ast.If(cond=new.cond, iftrue=new.stmt, iffalse=None)
         |            touched_old_size = len(touched)
         |            loop_body = c_ast.Compound([merge(old_statement, new_statement)])
         |            if (len(touched) == touched_old_size):
         |                break
         |
         |        merged_while = c_ast.While(cond=disjunct_while_cond, stmt=loop_body)
         |        return merged_while
         |
         |    # case 7 merge if conditions
         |    elif isinstance(new, c_ast.If) and isinstance(old, c_ast.If):
         |        # if with exactly same branching conditions. If prior difference has been detected,
         |        # conditional var will be renamed.
         |        if (str(new.cond) == str(old.cond)):
         |            return c_ast.If(cond=new.cond, iftrue=merge(old.iftrue, new.iftrue),
         |                            iffalse=merge(old.iffalse, new.iffalse))
         |
         |    # cannot merge if get here, check we need to versions variables.
         |    touched_set = mark_touched_variables(old, new)
         |    if (len(touched_set) > 0):
         |        rename_ID(old, new, list(touched_set))
         |    if (well_formed):
         |        return c_ast.Compound([old, new])
         |    else:
         |        return [old, new]
         |
         |
         |class DataSynVisitor(c_ast.NodeVisitor):
         |    def __init__(self, updateMap):
         |        self.update_map = updateMap
         |        self.parent_child = {}
         |        self.toupdate = {}
         |
         |    def reset(self):
         |        self.toupdate = {}
         |
         |    def syncData(self, node, parent, index=0):
         |        self.reset()
         |        self.visit(node, parent, index)
         |        self.update_exp()
         |
         |    def update_exp(self):
         |        for key in self.toupdate.keys():
         |            value = self.toupdate[key]
         |            key_parent = self.parent_child.get(key, None)
         |            child = key
         |            while (key_parent is not None and not isinstance(key_parent, c_ast.Compound)):
         |                child = key_parent
         |                key_parent = self.parent_child.get(key, None)
         |
         |            if (key_parent is not None and isinstance(key_parent, c_ast.Compound)):
         |                child_index = key_parent.block_items.index(child)
         |                key_parent.block_items.insert(child_index + 1, value)
         |
         |    def visit_Assignment(self, node, parent, index):
         |        if node.rvalue is not None and not isinstance(node.rvalue, c_ast.ID):
         |            self.visit(node.rvalue, node, 1)
         |        if node.lvalue is not None:
         |            if isinstance(node.lvalue, c_ast.ID):
         |                if node.lvalue.name in self.update_map:
         |                    update_targets = self.update_map[node.lvalue.name]
         |                    for target in update_targets:
         |                        node.rvalue = copy.deepcopy(node)
         |                        node.lvalue = c_ast.ID(name=target)
         |            else:
         |                self.visit(node.lvalue, node, 0)
         |
         |    def visit_Decl(self, node, parent, index):
         |        if isinstance(node, c_ast.Decl) and node.name in self.update_map:
         |            update_targets = self.update_map[node.name]
         |            if node.init is not None:
         |                if not isinstance(node.init, c_ast.ID):
         |                    self.visit(node.init, node, 1)
         |                for target in update_targets:
         |                    new_init = c_ast.Assignment(op='=', lvalue=c_ast.ID(name=target), rvalue=node.init)
         |                    node.init = new_init
         |
         |    def visit_UnaryOp(self, node, parent, index):
         |        if isinstance(node, c_ast.UnaryOp):
         |            if not (node.op.endswith("--") or node.op.endswith("++")):
         |                if not isinstance(node.expr, c_ast.ID):
         |                    self.visit(node.expr, node, 0)
         |            else:
         |                if not isinstance(node.expr, c_ast.ID):
         |                    self.visit(node.expr, node, 0)
         |                else:
         |                    if node.expr.name in self.update_map:
         |                        update_targets = self.update_map[node.expr.name]
         |                        old_op = node.op
         |                        for target in update_targets:
         |                            if (old_op.startswith('p')):
         |                                new_node = copy.deepcopy(node)
         |                                new_node.expr = c_ast.ID(name=target)
         |                                self.toupdate[node] = new_node
         |                                # node = c_ast.UnaryOp(expr = c_ast.Assignment(lvalue=c_ast.ID(name = target) , rvalue=node, op='='), op=old_op)
         |                            else:
         |                                node = c_ast.Assignment(lvalue=c_ast.ID(name=target), rvalue=node, op='=')
         |                        if isinstance(parent, c_ast.Compound):
         |                            parent.block_items[index] = node
         |                        elif isinstance(parent, c_ast.If):
         |                            if (index == 1):
         |                                parent.iftrue = node
         |                            elif (index == 2):
         |                                parent.iffalse = node
         |                        elif isinstance(parent, c_ast.For):
         |                            parent.stmt = node
         |                        elif isinstance(parent, c_ast.While):
         |                            parent.stmt = node
         |                        elif isinstance(parent, c_ast.Decl):
         |                            if index == 1:
         |                                parent.init = node
         |                        elif isinstance(parent, c_ast.Assignment):
         |                            if index == 0:
         |                                parent.lvalue = node
         |                            elif index == 1:
         |                                parent.rvalue = node
         |                        elif isinstance(parent, c_ast.BinaryOp):
         |                            if index == 0:
         |                                parent.left = node
         |                            elif index == 1:
         |                                parent.right = node
         |                        elif isinstance(parent, c_ast.UnaryOp):
         |                            if index == 0:
         |                                parent.expr = node
         |
         |    def visit(self, node, parent, index=0):
         |        \"\"\" Visit a node.
         |        \"\"\"
         |
         |        if self._method_cache is None:
         |            self._method_cache = {}
         |
         |        visitor = self._method_cache.get(node.__class__.__name__, None)
         |        if visitor is None:
         |            method = 'visit_' + node.__class__.__name__
         |            visitor = getattr(self, method, self.generic_visit)
         |            self._method_cache[node.__class__.__name__] = visitor
         |
         |        return visitor(node, parent, index)
         |
         |    def generic_visit(self, node, parent, index=0):
         |        \"\"\" Called if no explicit visitor function exists for a
         |            node. Implements preorder visiting of the node.
         |        \"\"\"
         |        counter = 0
         |        for c in node:
         |            self.parent_child[c] = node
         |            self.visit(c, node, counter)
         |            counter += 1
         |
         |
         |class DeclHunter(c_ast.NodeVisitor):
         |    def __init__(self, target):
         |        self.container = {}
         |        self.target = target
         |
         |    def visit_Decl(self, node):
         |        if isinstance(node, c_ast.Decl):
         |            if node.name in self.target:
         |                self.container[node.name] = copy.deepcopy(node.type)
         |
         |
         |def add_new_declares(node, signatures):
         |    global renamed
         |    global declared
         |
         |    undeclared = renamed - declared
         |    undeclared_name = set([name for name, version in undeclared])
         |    declhunter = DeclHunter(undeclared_name)
         |    if signatures is not None:
         |        declhunter.visit(signatures)
         |    init_by_arg = copy.deepcopy(declhunter.container)
         |    declhunter.visit(node)
         |    target_map = {}
         |    for (name, version) in undeclared:
         |        new_name = name + "_" + version
         |        if name in target_map:
         |            target_map[name] += [new_name]
         |        else:
         |            target_map[name] = [new_name]
         |        new_type = declhunter.container[name]
         |        cur_type = new_type
         |        while (not isinstance(cur_type, c_ast.TypeDecl)):
         |            cur_type = cur_type.type
         |        cur_type.declname = new_name
         |        if (name in init_by_arg):
         |            init_value = c_ast.ID(name=name)
         |        else:
         |            init_value = None
         |        node.block_items.insert(0, c_ast.Decl(
         |            name=new_name, quals=[], align=[], storage=[], init=init_value, funcspec=[],
         |            bitsize=None, type=c_ast.TypeDecl(declname=new_name, quals=[], align=[], type=new_type)))
         |
         |    syn = DataSynVisitor(target_map)
         |    syn.syncData(node, None, 0)
         |
         |
         |def paste_header_with_body(origin, body):
         |    origin_copy = copy.deepcopy(origin)
         |    origin_copy.body = body
         |    return origin_copy
         |
         |
         |if __name__ == '__main__':
         |    path_old = "${oldFilepath.toAbsolutePath.normalize()}"
         |    path_new = "${newFilepath.toAbsolutePath.normalize()}"
         |    lib = "$functionName"
         |    path_out = "${outFilepath.toAbsolutePath.normalize()}"
         |    lib_eq_assetion = True
         |
         |    old_ast = parse_file(path_old, use_cpp=True,
         |                         cpp_path='cpp',
         |                         cpp_args=['-I', pycparser_fake_libc.directory])
         |    new_ast = parse_file(path_new, use_cpp=True,
         |                         cpp_path='cpp',
         |                         cpp_args=['-I', pycparser_fake_libc.directory])
         |    # now look for lib from both versions
         |
         |    # generator = c_generator.CGenerator()
         |    # print(generator.visit(old_ast))
         |
         |    old_lib_visitor = FuncDefVisitor(lib)
         |    old_lib_visitor.visit(old_ast)
         |    old_lib_node = old_lib_visitor.container
         |    old_lib_copy = copy.deepcopy(old_lib_node)
         |    assert not old_lib_node is None, "old lib does not exist"
         |
         |    new_lib_visitor = FuncDefVisitor(lib)
         |    new_lib_visitor.visit(new_ast)
         |    new_lib_node = new_lib_visitor.container
         |    new_lib_copy = copy.deepcopy(new_lib_node)
         |    assert not new_lib_node is None, "new lib does not exist"
         |
         |    # check both lib have the same signature
         |    assert str(new_lib_node.decl.type) == str(old_lib_node.decl.type), "lib functions signature mismatch"
         |
         |    uitlity_class = []
         |    for ult in new_ast.ext:
         |        if isinstance(ult, c_ast.FuncDef):
         |            if not (ult.decl.name == lib or ult.decl.name == "main"):
         |                uitlity_class.append(copy.deepcopy(ult))
         |        elif isinstance(ult, c_ast.Decl):
         |            if not (ult.name == lib):
         |                uitlity_class.append(copy.deepcopy(ult))
         |        else:
         |            uitlity_class.append(copy.deepcopy(ult))
         |
         |    DTPV = DateTypeVisitor()
         |    DTPV.visit(new_lib_node)
         |    DTPV.visit(old_lib_node)
         |
         |    # convert returns into assignment
         |    r_types = get_type(old_lib_node)
         |    r_type = r_types[0]
         |    ret_v = ReturnHuntVisitor(r_type, "CLEVER_ret_{}_old", single_return=True)
         |    ret_v.visit(old_lib_node, None)
         |    old_lib_node.body.block_items.insert(0, c_ast.Decl(
         |        name="CLEVER_ret_0_old", quals=[], align=[], storage=[], init=constant_zero(), funcspec=[], bitsize=None,
         |        type=c_ast.TypeDecl(declname="CLEVER_ret_0_old", quals=[], align=[], type=c_ast.IdentifierType(r_types))))
         |    r_types = get_type(new_lib_node)
         |    r_type = r_types[0]
         |    ret_v = ReturnHuntVisitor(r_type, "CLEVER_ret_{}_new", single_return=True)
         |    ret_v.visit(new_lib_node, None)
         |    new_lib_node.body.block_items.insert(0, c_ast.Decl(
         |        name="CLEVER_ret_0_new", quals=[], align=[], storage=[], init=constant_zero(), funcspec=[], bitsize=None,
         |        type=c_ast.TypeDecl(declname="CLEVER_ret_0_new", quals=[], align=[], type=c_ast.IdentifierType(r_types))))
         |
         |    merged_ast = merge(old_lib_node.body, new_lib_node.body)
         |
         |    add_new_declares(merged_ast, new_lib_node.decl.type.args)
         |    merged_lib = paste_header_with_body(new_lib_node, merged_ast)
         |
         |    generator = c_generator.CGenerator()
         |
         |    if (lib_eq_assetion):
         |        merged_ast.block_items.append(c_ast.FuncCall(name=c_ast.ID(name='assert'), args=c_ast.BinaryOp(
         |            op='==', left=c_ast.ID(name="CLEVER_ret_0_old"), right=c_ast.ID(name="CLEVER_ret_0_new"))))
         |        params = merged_lib.decl.type.args
         |
         |        # check if main function already exists for the neccessary setup
         |        FDV = FuncDefVisitor("main")
         |        FDV.visit(new_ast)
         |        main_function = FDV.container
         |        if (main_function is None):
         |            main_function = copy.deepcopy(merged_lib)
         |            main_function.decl.name = "main"
         |            main_function.decl.type.type.declname = "main"
         |            main_function.decl.type.args = None
         |            main_function.body.block_items = []
         |        arg_list = []
         |        if params is not None:
         |            for item in params.params:
         |                main_function.body.block_items.append(copy.deepcopy(item))
         |                arg_list.append(c_ast.ID(name=item.name))
         |        main_function.body.block_items.append(
         |            c_ast.FuncCall(name=c_ast.ID(name=lib), args=c_ast.ExprList(exprs=arg_list)))
         |        m_file = c_ast.FileAST(ext=(uitlity_class + [main_function, merged_lib]))
         |
         |        with open(path_out, 'w') as out_file:
         |            out_file.write(generator.visit(m_file))
         |
         |""".stripMargin.getBytes())).!!

    Parser()(typechefArgs :+ outFilepath.toString)._1
  }
}
