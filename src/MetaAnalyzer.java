import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MetaAnalyzer {
    private Vector<Integer> lines; // contains locations of the lines of the program
    private Vector<Token> tokens; // vector of all tokens
    private Vector<SyntaxErrorReporter> syntaxErrors; // vector of all syntax errors
    private Vector<CodeElement> elements; // vector of all code elements
    private String source; // source code
    private Scope globalScope; // scope variable that contains the entire program

    enum TOKEN_TYPE {
        CHAR,
        STRING,
        BINARY,
        HEXADECIMAL,
        OCTAL,
        INTEGER,
        MODIFIER,
        TYPE,
        //CONTROL,
        KEYWORD,
        SYMBOL,
        IDENTIFIER,
    }

    private static final Pattern NEWLINE_REGEX = Pattern.compile("\\n", Pattern.MULTILINE);

    private static final Pattern TOKEN_REGEX = Pattern.compile("\\G(?<comments>(?://.*?$)|(?:/\\*.*\\*/))|" +
            "(?<whitespace>\\s+)|" +
            "(?<CHAR>'(?:(?:\\\\.)|(?:[^\\\\]))')|" +
            "(?<STRING>\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\")|" + // make as small a string as possible
            "(?<BINARY>0b[0-1]+)|" +
            "(?<HEXADECIMAL>-?0x[0-9a-fA-F]+)|" +
            "(?<OCTAL>-?0[0-9]+)|" +
            "(?<INTEGER>-?(?:(?:[1-9][0-9]*)|(?:0))[lL]?)|" +
            "(?<FLOAT>-?\\d*(?:(?:\\d+\\.)|(?:\\.\\d+))\\d*[fF]?)|" +
            "(?<MODIFIER>public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strictfp)|" +
            "(?<TYPE>boolean|char|double|float|byte|int|long)|" +
            "(?<KEYWORD>abstract|assert|boolean|break|byte|case|catch|char|class|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|null|false|const|goto)|" +
            "(?<SYMBOL>[{}()\\[\\].|\\\\,<>?/\\-+=!:@#;$%~^&*])|" +
            "(?<IDENTIFIER>\\b[_a-zA-Z$][\\w$]*\\b)", Pattern.DOTALL + Pattern.MULTILINE);

    /**
     * Documents an instance of a Syntax Error in the program
     */
    private class SyntaxErrorReporter {
        private int index;
        private String cause;

        /**
         * @param index which index of the tokens list
         * @param cause why is there a syntax error
         */
        public SyntaxErrorReporter(int index, String cause) {
            this.index = index;
            this.cause = cause;
        }

        @Override
        public String toString() {
            return "Syntax Error at index: " + index + ". Reason: " + cause;
        }
    }

    /**
     * A single token in the program
     */
    private class Token {
        private int srcBegin, srcEnd, index;
        public String text;
        public TOKEN_TYPE type;

        /**
         * @param begin where the token begins in source
         * @param end   where the token ends in source
         * @param index where the token is found in the token list
         * @param text  the String of the token
         * @param type  the TOKEN_TYPE of this token
         */
        public Token(int begin, int end, int index, String text, TOKEN_TYPE type) {
            this.srcBegin = begin;
            this.srcEnd = end;
            this.index = index;
            this.text = text;
            this.type = type;
        }

        @Override
        public String toString() {
            return UIHandler.rpad(type.toString(), 10) + " from " + UIHandler.rpad(srcBegin, 4) + " to " + UIHandler.rpad(srcEnd, 4) + " " + text;
        }

        /**
         * @return token that comes after the current one in the MetaAnalysis tokens list
         */
        public Token next() {
            if (this.index + 1 > tokens.size()) {
                return tokens.get(this.index + 1);
            } else {
                return null;
            }
        }

    }

    /**
     * An code element in the program
     */
    private abstract class CodeElement {
        Token begin;
        Token end;

        /**
         * @param tok_begin the token where the element begins
         * @param tok_end   the token where the element ends
         */
        CodeElement(Token tok_begin, Token tok_end) {
            begin = tok_begin;
            end = tok_end;
        }

        protected CodeElement() {
        }

        String getText() {
            return getLine(getLineNumber(begin.srcBegin));
        }

        public String toString() {
            return "AT " + begin + " to " + end + ":\n" + getText();
        }
    }

    private class Operator extends CodeElement {
        Operator(Token b, Token e) {
            super(b, e);
        }
    }

    private class Value extends CodeElement {
        Value(Token b, Token e) {
            super(b, e);
        }
    }

    private class Expression extends CodeElement {
        Expression(Token b, Token e) {
            super(b, e);
        }
    }

    /**
     * Represents pieces of code from a { to a }
     */
    private class Scope extends CodeElement {
        private Vector<Scope> subscopes;
        private Scope parentScope;

        /**
         * creates a new scope object by searching through the tokens list for matching braces
         * recursively constructs subscopes
         *
         * @param begin       token to start the scope from
         * @param parentScope the scope that contains the new scope
         */
        private Scope(Token begin, Scope parentScope) {
            super();
            subscopes = new Vector<>();
            // go through each token, when we end, current will be the matching { token
            Token current = begin;
            search:
            while (current != null) {
                switch (current.text) {
                    case "{":
                        Scope subscope = new Scope(current, this);
                        subscopes.add(subscope); // recursively add all subscopes
                        current = subscope.end; // jump ahead
                        break;
                    case "}":
                        break search;
                    default:
                        current = current.next();
                }
            }
            this.begin = begin;
            this.end = current;
            this.parentScope = parentScope;
        }

        /**
         * recursively prints out each scope, including its preceding token if possible
         */
        public void printOut() {
            printOut("");
        }

        /**
         * recursively prints out each scope, including its preceding token if possible
         *
         * @param prepend string to prepend to each line printed out
         */
        public void printOut(String prepend) {
            String precedingTokenText = begin.index > 0 ? tokens.get(begin.index - 1).text : "global";
            System.out.println(prepend + "╚═" + precedingTokenText);
            for (Scope subscope : subscopes) {
                subscope.printOut("  " + prepend);
            }
        }


    }

    private CodeElement buildCodeElement(String type, Token tok_begin, Token tok_end) {
        switch (type) {
            case "OPERATOR":
                return new Operator(tok_begin, tok_end);
            case "VALUE":
                return new Value(tok_begin, tok_end);
            case "EXPRESSION":
                return new Expression(tok_begin, tok_end);
            default:
                throw new RuntimeException("bad CodeElement type: " + type);
        }
    }

    private class ParseNode {
        private final HashMap<TOKEN_TYPE, ParseNode> childNodes;
        Token token;
        private ParseNode parent = null;

        public ParseNode(HashMap<TOKEN_TYPE, ParseNode> childNodes) {
            this.childNodes = childNodes;
        }

        ParseNode() {
            this.childNodes = new HashMap<>();
        }

        ParseNode nextNode(Token tok) {
            ParseNode nextNode = childNodes.get(tok.type);
            this.token = tok;
            nextNode.addParent(this);
            return nextNode;
        }

        private void addParent(ParseNode parseNode) {
            this.parent = parseNode;
        }

        public void addChild(TOKEN_TYPE type, ParseNode child) {
            childNodes.put(type, child);
            child.addParent(this);
        }

        ParseNode getRoot() {
            if (parent == null) {
                return this;
            } else {
                return parent.getRoot();
            }
        }
    }

    private class ParseLeaf extends ParseNode {
        public String type;

        public ParseLeaf(String type) {
            this.type = type;
        }

        CodeElement generateResult() {
            return buildCodeElement(type, getRoot().token, token);
        }
    }

    private static void buildSyntaxTreeOn(ParseNode node) {

    }

    private static ParseNode rootSyntaxNode;

    MetaAnalyzer(String src) {
        source = src;
        tokens = new Vector<>();
        elements = new Vector<>();
        lines = new Vector<>();
        syntaxErrors = new Vector<>();

        rootSyntaxNode = new ParseNode();
        buildSyntaxTreeOn(rootSyntaxNode);

        // build up the lines vector
        determineLines();

        // identify and list out the tokens
        tokenize();

        // build up scopes
        globalScope = new Scope(tokens.firstElement(), null);
        globalScope.printOut();

        // analyze all other statements
//        analyzeElements();
 //       UIHandler.printCollection(elements, "Found {} code elements");
    }

    /**
     * find and store the indecis of all line breaks
     */
    private void determineLines() {
        Matcher match = NEWLINE_REGEX.matcher(source);

        while (match.find()) {
            assert match.start() == match.end();
            lines.add(match.start());
        }

    }


    /**
     * @param index index of character in source file
     * @return index of line break that this character belongs to
     */
    public int getLineNumber(int index) {
        int lo = 0;
        int hi = lines.size() - 1;
        while (true) {
            int guess = (lo + hi) / 2;
            boolean indexIsAfterStartOfLine = lines.get(guess) < index;
            boolean indexIsBeforeEndOfLine = ((guess + 1) == lines.size()) || (index <= lines.get(guess + 1));
            if (indexIsAfterStartOfLine && indexIsBeforeEndOfLine) {
                // we got a classic case of it works
                return guess;
            } else if (!indexIsAfterStartOfLine) {
                // index is before the start of the line
                // move the hi down
                hi = guess;
            } else {
                // index is after the end of the line
                // move the lo up
                lo = guess;
            }
        }
    }

    /**
     * @param lineNumber the number of the line to get
     * @return returns a substring of a single line in the program
     */
    public String getLine(int lineNumber) {
        return source.substring(lines.get(lineNumber), lineNumber + 1 > lines.size() ? source.length() : lines.get(lineNumber) - 1);
    }

    /**
     * initialize the list of all tokens,
     * ignoring comments and white space
     */
    private void tokenize() {
        Matcher match = TOKEN_REGEX.matcher(source);

        while (match.find()) {

            for (TOKEN_TYPE type : TOKEN_TYPE.values()) {
                String text = match.group(type.toString());
                if (text != null) {
                    tokens.add(new Token(match.start(), match.end(), tokens.size(), text, type));
                }
            }

        }
    }

    /**
     * using the list of tokens,
     * create the list of code elements
     */
    private void analyzeElements() {

        ParseNode currentNode = rootSyntaxNode;
        Token firstToken = tokens.firstElement();

        for (Token currentToken : tokens) {

            currentNode = currentNode.nextNode(currentToken);

            if (currentNode instanceof ParseLeaf) {
                ParseLeaf node = (ParseLeaf) currentNode;
                elements.add(buildCodeElement(node.type, firstToken, currentToken));
                currentNode = rootSyntaxNode;
                firstToken = currentToken;
            }

        }
    }
}