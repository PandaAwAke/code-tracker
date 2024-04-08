package net.sourceforge.pmd;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.LanguageVersionHandler;
import net.sourceforge.pmd.lang.Parser;
import net.sourceforge.pmd.lang.ParserOptions;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ParseException;
import net.sourceforge.pmd.lang.xpath.Initializer;
import net.sourceforge.pmd.util.Benchmark;
import net.sourceforge.pmd.util.IOUtil;

public class SourceCodeProcessor {

    private final Configuration configuration;

    public SourceCodeProcessor(Configuration configuration) {
    	this.configuration = configuration;
    }
    
    
    /**
     * Processes the input stream against a rule set using the given input encoding.
     *
     * @param sourceCode The InputStream to analyze.
     * @param ruleSets The collection of rules to process against the file.
     * @param ctx The context in which PMD is operating.
     * @throws PMDException if the input encoding is unsupported, the input stream could
     *                      not be parsed, or other error is encountered.
     * @see #processSourceCode(Reader, RuleSets, RuleContext)
     */
    public void processSourceCode(InputStream sourceCode, RuleSets ruleSets, RuleContext ctx) throws PMDException {
		try {
		    processSourceCode(new InputStreamReader(sourceCode, this.configuration.getSourceEncoding()), ruleSets, ctx);
		} catch (UnsupportedEncodingException uee) {
		    throw new PMDException("Unsupported encoding exception: " + uee.getMessage());
		}
    }
    
    
    /**
     * Processes the input stream against a rule set using the given input encoding.
     * If the LanguageVersion is <code>null</code>  on the RuleContext, it will
     * be automatically determined.  Any code which wishes to process files for
     * different Languages, will need to be sure to either properly set the
     * Language on the RuleContext, or set it to <code>null</code> first.
     *
     * @see RuleContext#setLanguageVersion(LanguageVersion)
     * @see Configuration#getLanguageVersionOfFile(String)
     *
     * @param sourceCode The Reader to analyze.
     * @param ruleSets The collection of rules to process against the file.
     * @param ctx The context in which PMD is operating.
     * @throws PMDException if the input encoding is unsupported, the input stream could
     *                      not be parsed, or other error is encountered.
     */
    public void processSourceCode(final Reader sourceCode, final RuleSets ruleSets, final RuleContext ctx) throws PMDException {
    	determineLanguage(ctx);

		// make sure custom XPath functions are initialized
		Initializer.initialize();

	    // Coarse check to see if any RuleSet applies to file, will need to do a finer RuleSet specific check later
		 if (ruleSets.applies(ctx.getSourceCodeFile())) {

		try {
			processSource(sourceCode, ruleSets,ctx);

		} catch (ParseException pe) {
		    throw new PMDException("Error while parsing " + ctx.getSourceCodeFilename(), pe);
		} catch (Exception e) {
		    throw new PMDException("Error while processing " + ctx.getSourceCodeFilename(), e);
		} finally {
		    IOUtil.closeQuietly(sourceCode);
		}
		}
    }

    
    private Node parse(final RuleContext ctx, final Reader sourceCode, final Parser parser) {
		final long start = System.nanoTime();
		Node rootNode = parser.parse(ctx.getSourceCodeFilename(), sourceCode);
		ctx.getReport().suppress(parser.getSuppressMap());
		final long end = System.nanoTime();    	
		Benchmark.mark(Benchmark.TYPE_PARSER, end - start, 0);
		return rootNode;
    }

    private void symbolFacade(final Node rootNode, final LanguageVersionHandler languageVersionHandler) {
    	long start = System.nanoTime();
		languageVersionHandler.getSymbolFacade().start(rootNode);
		long end = System.nanoTime();
		Benchmark.mark(Benchmark.TYPE_SYMBOL_TABLE, end - start, 0);
    }
    
    private ParserOptions getParserOptions(final LanguageVersionHandler languageVersionHandler) {
		// TODO Handle Rules having different parser options.
		ParserOptions parserOptions = languageVersionHandler.getDefaultParserOptions();
		parserOptions.setSuppressMarker(configuration.getSuppressMarker());
		return parserOptions;
    }

    private void usesDFA(final LanguageVersion languageVersion, final Node rootNode, final  RuleSets ruleSets, final Language language ) {

		if (ruleSets.usesDFA(language)) {
		    final long start = System.nanoTime();
		    languageVersion.getLanguageVersionHandler().getDataFlowFacade().start(rootNode);
		    final long end = System.nanoTime();
		    Benchmark.mark(Benchmark.TYPE_DFA, end - start, 0);
		}

    }

    private void usesTypeResolution(final LanguageVersion languageVersion, final Node rootNode, final  RuleSets ruleSets, final Language language) {
	
		if (ruleSets.usesTypeResolution(language)) {
		    final long start = System.nanoTime();
		    languageVersion.getLanguageVersionHandler().getTypeResolutionFacade(configuration.getClassLoader()).start(rootNode);
		    final long end = System.nanoTime();
		    Benchmark.mark(Benchmark.TYPE_TYPE_RESOLUTION, end - start, 0);
		}

    }
    
    private void processSource(final Reader sourceCode, final RuleSets ruleSets, final RuleContext ctx) {
		LanguageVersion languageVersion = ctx.getLanguageVersion();
		LanguageVersionHandler languageVersionHandler = languageVersion.getLanguageVersionHandler();
		Parser parser = languageVersionHandler.getParser(getParserOptions(languageVersionHandler));
		
		Node rootNode = parse(ctx, sourceCode, parser);
		symbolFacade(rootNode, languageVersionHandler);
		Language language = languageVersion.getLanguage();
		usesDFA(languageVersion, rootNode, ruleSets, language);
		usesTypeResolution(languageVersion, rootNode, ruleSets,language);
		
		List<Node> acus = new ArrayList<Node>();
		acus.add(rootNode);
		ruleSets.apply(acus, ctx, language);		
	}



	private void determineLanguage(final RuleContext ctx) {
		// If LanguageVersion of the source file is not known, make a determination
		if (ctx.getLanguageVersion() == null) {
		    LanguageVersion languageVersion = configuration.getLanguageVersionOfFile(ctx.getSourceCodeFilename());
		    ctx.setLanguageVersion(languageVersion);
		}
    }
}
