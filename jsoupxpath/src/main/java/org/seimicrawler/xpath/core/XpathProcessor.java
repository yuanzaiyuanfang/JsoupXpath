package org.seimicrawler.xpath.core;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.seimicrawler.xpath.antlr.XpathBaseVisitor;
import org.seimicrawler.xpath.exception.XpathMergeValueException;
import org.seimicrawler.xpath.exception.XpathParserException;
import org.seimicrawler.xpath.util.CommonUtil;
import org.seimicrawler.xpath.util.Scanner;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import static org.seimicrawler.xpath.antlr.XpathParser.AbbreviatedStepContext;
import static org.seimicrawler.xpath.antlr.XpathParser.AbsoluteLocationPathNorootContext;
import static org.seimicrawler.xpath.antlr.XpathParser.AdditiveExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.AndExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.AxisSpecifierContext;
import static org.seimicrawler.xpath.antlr.XpathParser.CONTAIN_WITH;
import static org.seimicrawler.xpath.antlr.XpathParser.DIVISION;
import static org.seimicrawler.xpath.antlr.XpathParser.END_WITH;
import static org.seimicrawler.xpath.antlr.XpathParser.EqualityExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.ExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.FilterExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.FunctionCallContext;
import static org.seimicrawler.xpath.antlr.XpathParser.FunctionNameContext;
import static org.seimicrawler.xpath.antlr.XpathParser.GE;
import static org.seimicrawler.xpath.antlr.XpathParser.LE;
import static org.seimicrawler.xpath.antlr.XpathParser.LESS;
import static org.seimicrawler.xpath.antlr.XpathParser.LocationPathContext;
import static org.seimicrawler.xpath.antlr.XpathParser.MODULO;
import static org.seimicrawler.xpath.antlr.XpathParser.MORE_;
import static org.seimicrawler.xpath.antlr.XpathParser.MUL;
import static org.seimicrawler.xpath.antlr.XpathParser.MainContext;
import static org.seimicrawler.xpath.antlr.XpathParser.MultiplicativeExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.NCNameContext;
import static org.seimicrawler.xpath.antlr.XpathParser.NameTestContext;
import static org.seimicrawler.xpath.antlr.XpathParser.NodeTestContext;
import static org.seimicrawler.xpath.antlr.XpathParser.OrExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.PathExprNoRootContext;
import static org.seimicrawler.xpath.antlr.XpathParser.PredicateContext;
import static org.seimicrawler.xpath.antlr.XpathParser.PrimaryExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.QNameContext;
import static org.seimicrawler.xpath.antlr.XpathParser.REGEXP_NOT_WITH;
import static org.seimicrawler.xpath.antlr.XpathParser.REGEXP_WITH;
import static org.seimicrawler.xpath.antlr.XpathParser.RelationalExprContext;
import static org.seimicrawler.xpath.antlr.XpathParser.RelativeLocationPathContext;
import static org.seimicrawler.xpath.antlr.XpathParser.START_WITH;
import static org.seimicrawler.xpath.antlr.XpathParser.StepContext;
import static org.seimicrawler.xpath.antlr.XpathParser.UnaryExprNoRootContext;
import static org.seimicrawler.xpath.antlr.XpathParser.UnionExprNoRootContext;

/**
 * @author github.com/zhegexiaohuozi seimimaster@gmail.com
 * @since 2017/8/30.
 */
public class XpathProcessor extends XpathBaseVisitor<XValue> {
    private Stack<Scope> scopeStack = new Stack<>();
    private Scope rootScope;
    public XpathProcessor(Elements root){
        rootScope = Scope.create(root);
        scopeStack.push(Scope.create(root).setParent(rootScope));
    }

    @Override
    public XValue visitMain(MainContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public XValue visitLocationPath(LocationPathContext ctx) {
        if (ctx.relativeLocationPath()!=null&&!ctx.relativeLocationPath().isEmpty()){
            return visit(ctx.relativeLocationPath());
        }
        return visit(ctx.absoluteLocationPathNoroot());
    }

    @Override
    public XValue visitAbsoluteLocationPathNoroot(AbsoluteLocationPathNorootContext ctx) {
        // '//'
        if (Objects.equals(ctx.op.getText(),"//")){
            currentScope().recursion();
        }
        return visit(ctx.relativeLocationPath());
    }

    @Override
    public XValue visitRelativeLocationPath(RelativeLocationPathContext ctx) {
        XValue finalVal = null;
        for (int i = 0;i<ctx.getChildCount();i++){
            ParseTree step = ctx.getChild(i);
            if (step instanceof StepContext){
                finalVal = visit(step);
                if (finalVal.isElements()){
                    updateCurrentContext(finalVal.asElements());
                }
            }else {
                if ("//".equals(step.getText())){
                    currentScope().recursion();
                }else {
                    currentScope().notRecursion();
                }
            }
        }
        return finalVal;
    }

    @Override
    public XValue visitStep(StepContext ctx) {
        if (ctx.abbreviatedStep()!=null&&!ctx.abbreviatedStep().isEmpty()){
            return visit(ctx.abbreviatedStep());
        }
        boolean filterByAttr = false;
        boolean isAxisOk = false;
        if (ctx.axisSpecifier()!=null&&!ctx.axisSpecifier().isEmpty()){
            XValue axis = visit(ctx.axisSpecifier());
            if (axis!=null){
                isAxisOk = true;
                if (axis.isElements()){
                    updateCurrentContext(axis.asElements());
                }else if (axis.isAttr()){
                    filterByAttr = true;
                }
            }
        }
        if (ctx.nodeTest()!=null&&!ctx.nodeTest().isEmpty()){
            XValue nodeTest = visit(ctx.nodeTest());
            if (filterByAttr){
                Elements context = currentScope().context();
                String attrName = nodeTest.asString();
                if (currentScope().isRecursion()){
                    if (context.size() == 1){
                        Element el = currentScope().singleEl();
                        Elements findRes = el.select("["+attrName+"]");
                        List<String> attrs = new LinkedList<>();
                        for (Element e:findRes){
                            attrs.add(e.attr(attrName));
                        }
                        return XValue.create(attrs);
                    }else {
                        Elements findRes = new Elements();
                        for (Element el:context){
                            findRes.addAll(el.select("["+attrName+"]"));
                        }
                        List<String> attrs = new LinkedList<>();
                        for (Element e:findRes){
                            attrs.add(e.attr(attrName));
                        }
                        return XValue.create(attrs);
                    }
                }else {
                    if (context.size() == 1){
                        Element el = currentScope().singleEl();
                        return XValue.create(el.attr(attrName));
                    }else {
                        List<String> attrs = new LinkedList<>();
                        for (Element el:context){
                            attrs.add(el.attr(attrName));
                        }
                        return XValue.create(attrs);
                    }
                }

            }else {
                if (nodeTest.isExprStr()){
                    String tagName = nodeTest.asString();
                    Elements current = currentScope().context();
                    if (currentScope().isRecursion()){
                        updateCurrentContext(current.select(tagName));
                    }else {
                        Elements newContext = new Elements();
                        for (Element el:currentScope().context()){
                            if (isAxisOk){
                                if (el.nodeName().equals(tagName)||"*".equals(tagName)){
                                    newContext.add(el);
                                }
                            }else {
                                for (Element e:el.children()){
                                    if (e.nodeName().equals(tagName)||"*".equals(tagName)){
                                        newContext.add(e);
                                    }
                                }
                            }

                        }
                        updateCurrentContext(newContext);
                    }
                }else {
                    // nodeType ?????????????????????????????????
                    return nodeTest;
                }
            }
        }
        if (ctx.predicate()!=null&&ctx.predicate().size()>0){
            for(PredicateContext predicate: ctx.predicate()){
                XValue predicateVal = visit(predicate);
                updateCurrentContext(predicateVal.asElements());
            }
        }
        return XValue.create(currentScope().context());
    }

    @Override
    public XValue visitAbbreviatedStep(AbbreviatedStepContext ctx) {
        if ("..".equals(ctx.getText())){
            Set<Element> total = new HashSet<>();
            Elements newContext = new Elements();
            for (Element e:currentScope().context()){
                total.add(e.parent());
            }
            newContext.addAll(total);
            return XValue.create(newContext);
        }else {
            return XValue.create(currentScope().context());
        }
    }

    @Override
    public XValue visitAxisSpecifier(AxisSpecifierContext ctx) {
        TerminalNode axisNode = ctx.AxisName();
        if (axisNode != null){
            String axis = ctx.AxisName().getText();
            AxisSelector axisSelector = Scanner.findSelectorByName(axis);
            return axisSelector.apply(currentScope().context());
        }else {
            String token = ctx.getText();
            if ("@".equals(token)){
                return XValue.create(null).attr();
            }
        }
        return null;
    }

    @Override
    public XValue visitNodeTest(NodeTestContext ctx) {
        if (ctx.nameTest()!=null){
            return visit(ctx.nameTest());
        }if (ctx.NodeType()!=null){
            NodeTest nodeTest = Scanner.findNodeTestByName(ctx.NodeType().getText());
            return nodeTest.call(currentScope());
        }
        // todo:   |  'processing-instruction' '(' Literal ')'
        return null;
    }

    @Override
    public XValue visitPredicate(PredicateContext ctx) {
        Elements newContext = new Elements();
        for (Element e:currentScope().context()){
            scopeStack.push(Scope.create(e).setParent(currentScope()));
            XValue exprVal = visit(ctx.expr());
            scopeStack.pop();
            if (exprVal.isNumber()){
                long index = exprVal.asLong();
                if (index < 0){
                    index = CommonUtil.sameTagElNums(e,currentScope()) + index + 1;
                }
                if (index == CommonUtil.getElIndexInSameTags(e,currentScope())){
                    newContext.add(e);
                }
            }else if (exprVal.isBoolean()){
                if (exprVal.asBoolean()){
                    newContext.add(e);
                }
            }else if (exprVal.isString()){
                //???????????????????????????????????????????????????,??? //*[@foo]
                if (StringUtils.isNotBlank(exprVal.asString())){
                    newContext.add(e);
                }
            }else if (exprVal.isElements()){
                //????????????????????????????????????????????????????????? //div[./a]
                Elements els = exprVal.asElements();
                if (els.size()>0){
                    newContext.add(e);
                }
            }else if (exprVal.isList()){
                //????????????????????????????????????????????????????????? //div[./a/text()]
                List<String> stringList = exprVal.asList();
                if (stringList.size()>0){
                    newContext.add(e);
                }
            }else {
                throw new XpathParserException("unknown expr val:"+exprVal);
            }
        }
        return XValue.create(newContext);
    }

    @Override
    public XValue visitNameTest(NameTestContext ctx) {
        if ("*".equals(ctx.getText())){
            return XValue.create("*").exprStr();
        }else if (ctx.qName() != null&&!ctx.qName().isEmpty()){
            return visit(ctx.qName());
        }else if (ctx.nCName()!=null&&!ctx.nCName().isEmpty()){
            return visit(ctx.nCName());
        }
        return null;
    }

    @Override
    public XValue visitQName(QNameContext ctx) {
        List<NCNameContext> ncNameContexts =ctx.nCName();
        if (ncNameContexts!=null){
            if (ncNameContexts.size()>1){
                List<String> ncNames = new LinkedList<>();
                for (NCNameContext ncNameContext:ncNameContexts){
                    XValue value = visit(ncNameContext);
                    if (value!=null){
                        ncNames.add(value.asString());
                    }
                }
                // TODO: 2018/3/14 ??????????????????????????????
                return XValue.create(StringUtils.join(ncNames,":"));
            }else {
                return visit(ncNameContexts.get(0));
            }
        }
        return null;
    }

    @Override
    public XValue visitNCName(NCNameContext ctx) {
        if (ctx.AxisName()!=null){
            return XValue.create(ctx.AxisName().getText()).exprStr();
        }else {
            return XValue.create(ctx.NCName().getText()).exprStr();
        }
    }

    @Override
    public XValue visitExpr(ExprContext ctx) {
        return visit(ctx.orExpr());
    }

    @Override
    public XValue visitOrExpr(OrExprContext ctx) {
        List<AndExprContext> andExprContexts = ctx.andExpr();
        if (andExprContexts.size()>1){
            Boolean res = visit(andExprContexts.get(0)).asBoolean();
            for (int i=1;i<andExprContexts.size();i++){
                res = (res | visit(andExprContexts.get(i)).asBoolean());
            }
            return XValue.create(res);
        }else {
            return visit(andExprContexts.get(0));
        }
    }

    @Override
    public XValue visitAndExpr(AndExprContext ctx) {
        List<EqualityExprContext> equalityExprContexts = ctx.equalityExpr();
        if (equalityExprContexts.size()>1){
            Boolean res = visit(equalityExprContexts.get(0)).asBoolean();
            for (int i=1;i<equalityExprContexts.size();i++){
                res = (res & visit(equalityExprContexts.get(i)).asBoolean());
            }
            return XValue.create(res);
        }else {
            return visit(equalityExprContexts.get(0));
        }
    }

    @Override
    public XValue visitEqualityExpr(EqualityExprContext ctx) {
        List<RelationalExprContext> relationalExprContexts = ctx.relationalExpr();
        if (relationalExprContexts.size()==1){
            return visit(relationalExprContexts.get(0));
        }else if (relationalExprContexts.size()==2){
            XValue left = visit(relationalExprContexts.get(0));
            XValue right = visit(relationalExprContexts.get(1));
            if ("=".equals(ctx.op.getText())){
                if (left.valType().equals(right.valType())){
                    return XValue.create(Objects.equals(left ,right));
                }else {
                    return XValue.create(Objects.equals(left.asString() ,right.asString()));
                }
            }else {
                if (left.valType().equals(right.valType())){
                    return XValue.create(!Objects.equals(left ,right));
                }else {
                    return XValue.create(!Objects.equals(left.asString() ,right.asString()));
                }
            }
        }else {
            throw new XpathParserException("error equalityExpr near:"+ctx.getText());
        }
    }

    @Override
    public XValue visitRelationalExpr(RelationalExprContext ctx) {
        List<AdditiveExprContext> additiveExprContexts = ctx.additiveExpr();
        if (additiveExprContexts.size() == 1){
            return visit(additiveExprContexts.get(0));
        }else if (additiveExprContexts.size()==2){
            XValue left = visit(additiveExprContexts.get(0));
            XValue right = visit(additiveExprContexts.get(1));
            switch (ctx.op.getType()){
                case MORE_:
                    return XValue.create(left.compareTo(right) > 0);
                case GE:
                    return XValue.create(left.compareTo(right) >= 0);
                case LESS:
                    return XValue.create(left.compareTo(right) < 0);
                case LE:
                    return XValue.create(left.compareTo(right) <= 0);
                case START_WITH:
                    return XValue.create(left.asString().startsWith(right.asString()));
                case END_WITH:
                    return XValue.create(left.asString().endsWith(right.asString()));
                case CONTAIN_WITH:
                    return XValue.create(left.asString().contains(right.asString()));
                case REGEXP_WITH:
                    return XValue.create(left.asString().matches(right.asString()));
                case REGEXP_NOT_WITH:
                    return XValue.create(!left.asString().matches(right.asString()));
                default:
                    throw new XpathParserException("unknown operator"+ctx.op.getText());
            }
        }else {
            throw new XpathParserException("error equalityExpr near:"+ctx.getText());
        }
    }

    @Override
    public XValue visitAdditiveExpr(AdditiveExprContext ctx) {
        List<MultiplicativeExprContext> multiplicativeExprContexts = ctx.multiplicativeExpr();
        if (multiplicativeExprContexts.size() == 1){
            return visit(multiplicativeExprContexts.get(0));
        }else {
            Double res = visit(multiplicativeExprContexts.get(0)).asDouble();
            String op = null;
            for (int i=1;i<ctx.getChildCount();i++){
                ParseTree chiCtx = ctx.getChild(i);
                if (chiCtx instanceof MultiplicativeExprContext){
                    XValue next = visit(chiCtx);
                    if ("+".equals(op)){
                        res+=next.asDouble();
                    }else if ("-".equals(op)){
                        res-=next.asDouble();
                    }else {
                        throw new XpathParserException("syntax error, "+ctx.getText());
                    }
                }else {
                    op = chiCtx.getText();
                }
            }
            return XValue.create(res);
        }
    }

    @Override
    public XValue visitMultiplicativeExpr(MultiplicativeExprContext ctx) {
        if (ctx.multiplicativeExpr() == null||ctx.multiplicativeExpr().isEmpty()){
            return visit(ctx.unaryExprNoRoot());
        }else {
            XValue left = visit(ctx.unaryExprNoRoot());
            XValue right = visit(ctx.multiplicativeExpr());
            switch (ctx.op.getType()){
                case MUL:
                    return XValue.create(left.asDouble() * right.asDouble());
                case DIVISION:
                    return XValue.create(left.asDouble() / right.asDouble());
                case MODULO:
                    return XValue.create(left.asDouble() % right.asDouble());
                default:
                    throw new XpathParserException("syntax error, "+ctx.getText());
            }
        }
    }

    @Override
    public XValue visitUnaryExprNoRoot(UnaryExprNoRootContext ctx) {
        XValue value = visit(ctx.unionExprNoRoot());
        if (ctx.sign == null){
            return value;
        }
        return XValue.create(-value.asDouble());
    }

    @Override
    public XValue visitUnionExprNoRoot(UnionExprNoRootContext ctx) {
        if (ctx.pathExprNoRoot()== null&&!ctx.pathExprNoRoot().isEmpty()){
            return visit(ctx.unionExprNoRoot());
        }
        XValue pathExprNoRoot = visit(ctx.pathExprNoRoot());
        if (ctx.op == null){
            return pathExprNoRoot;
        }
        scopeStack.push(Scope.create(currentScope().getParent()));
        XValue unionExprNoRoot = visit(ctx.unionExprNoRoot());
        scopeStack.pop();
        if (pathExprNoRoot.isElements()){
            if (unionExprNoRoot.isElements()){
                pathExprNoRoot.asElements().addAll(unionExprNoRoot.asElements());
            }else {
                Element element = new Element("V");
                element.appendText(unionExprNoRoot.asString());
                pathExprNoRoot.asElements().add(element);
            }
            return pathExprNoRoot;
        }else if (pathExprNoRoot.isString()){
            if (unionExprNoRoot.isElements()){
                Element element = new Element("V");
                element.appendText(pathExprNoRoot.asString());
                unionExprNoRoot.asElements().add(element);
                return unionExprNoRoot;
            }else {
                return XValue.create(pathExprNoRoot.asString()+unionExprNoRoot.asString());
            }
        }else if (pathExprNoRoot.isBoolean()){
            if (unionExprNoRoot.isBoolean()){
                return XValue.create(pathExprNoRoot.asBoolean()|unionExprNoRoot.asBoolean());
            }else if (unionExprNoRoot.isElements()){
                Element element = new Element("V");
                element.appendText(pathExprNoRoot.asString());
                unionExprNoRoot.asElements().add(element);
                return unionExprNoRoot;
            }else if (unionExprNoRoot.isString()){
                return XValue.create(pathExprNoRoot.asBoolean()+unionExprNoRoot.asString());
            }else {
                throw new XpathMergeValueException("can not merge val1="+pathExprNoRoot.asBoolean()+",val2="+unionExprNoRoot.asString());
            }
        }else if (pathExprNoRoot.isNumber()){
            if (unionExprNoRoot.isString()){
                return XValue.create(pathExprNoRoot.asDouble()+unionExprNoRoot.asString());
            }else if (unionExprNoRoot.isElements()){
                Element element = new Element("V");
                element.appendText(pathExprNoRoot.asString());
                unionExprNoRoot.asElements().add(element);
                return unionExprNoRoot;
            }else {
                throw new XpathMergeValueException("can not merge val1="+pathExprNoRoot.asDouble()+",val2="+unionExprNoRoot.asString());
            }
        }else {
            List<String> tmpVal = new LinkedList<>();
            if (StringUtils.isNotBlank(pathExprNoRoot.asString())){
                tmpVal.add(pathExprNoRoot.asString());
            }
            if (StringUtils.isNotBlank(unionExprNoRoot.asString())){
                tmpVal.add(unionExprNoRoot.asString());
            }
            return XValue.create(StringUtils.join(tmpVal,","));
        }
    }

    @Override
    public XValue visitPathExprNoRoot(PathExprNoRootContext ctx) {
        if (ctx.locationPath()!=null&&!ctx.locationPath().isEmpty()){
            return visit(ctx.locationPath());
        }
        if (ctx.op == null){
            return visit(ctx.filterExpr());
        }
        if ("//".equals(ctx.op.getText())){
            currentScope().recursion();
        }
        return visit(ctx.relativeLocationPath());
    }

    @Override
    public XValue visitFilterExpr(FilterExprContext ctx) {
        //????????????????????????   :  primaryExpr predicate*
        return visit(ctx.primaryExpr());
    }

    @Override
    public XValue visitPrimaryExpr(PrimaryExprContext ctx) {
        if (ctx.expr()!=null&&!ctx.expr().isEmpty()){
            return visit(ctx.expr());
        }else if (ctx.functionCall()!=null&&!ctx.functionCall().isEmpty()){
            return visit(ctx.functionCall());
        }else if (ctx.Literal()!=null){
            return XValue.create(ctx.Literal().getText()).exprStr();
        }else if (ctx.Number()!=null){
            return XValue.create(NumberUtils.createDouble(ctx.Number().getText()));
        }
        throw new XpathParserException("not support variableReference:"+ctx.getText());
    }

    @Override
    public XValue visitFunctionCall(FunctionCallContext ctx) {
        List<XValue> params = new LinkedList<>();
        XValue funcName = visit(ctx.functionName());
        for (ExprContext exprContext:ctx.expr()){
            scopeStack.push(Scope.create(currentScope()));
            params.add(visit(exprContext));
            scopeStack.pop();
        }
        Function function = Scanner.findFunctionByName(funcName.asString());
        return function.call(currentScope(),params);
    }

    @Override
    public XValue visitFunctionName(FunctionNameContext ctx) {
        return visit(ctx.qName());
    }

    private Scope currentScope(){
        return scopeStack.peek();
    }

    private void updateCurrentContext(Elements newContext){
        scopeStack.peek().setContext(newContext);
    }

}
