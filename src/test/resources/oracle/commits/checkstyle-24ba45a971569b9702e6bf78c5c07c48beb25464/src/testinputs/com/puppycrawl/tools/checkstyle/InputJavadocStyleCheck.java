////////////////////////////////////////////////////////////////////////////////
// Test case file for checkstyle.
// Created: 2003
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle;

/**
 * Test input for the JavadocStyleCheck.  This check is used to perform 
 * some additional Javadoc validations.  
 * 
 * @author Chris Stillwell
 * @version 1.0
 */
public class InputJavadocStyleCheck
{
   // This is OK. We don't flag missing javadoc.  That's left for other checks.
   private String first;
   
   /** This Javadoc is missing an ending period */
   private String second;
   
   /**
    * We don't want {@link com.puppycrawl.tools.checkstyle.checks.JavadocStyleCheck} 
    * tags to stop the scan for the end of sentence. 
    * @see Somthing
    */
   public InputJavadocStyleCheck()
   {
   }
   
   /**
    * This is ok!
    */
   private void method1()
   {
   }
   
   /**
    * This is ok?
    */
   private void method2()
   {
   }
   
   /**
    * And This is ok.<br>
    */
   private void method3()
   {
   }
   
   /**
    * This should fail even.though.there are embedded periods
    */
   private void method4()
   {
   }
   
   /**
    * Test HTML in Javadoc comment
    * <dl>
    * <dt><b>This guy is missing end of bold tag
    * <dd>The dt and dd don't require end tags.
    * </dl>
    * </td>Extra tag shouldn't be here
    * 
    * @param arg1 <code>dummy.
    */
   private void method5(int arg1)
   {
   }
   
   /**
    * Protected check <b>should fail
    */
   protected void method6()
   {
   }
   
   /**
    * Package protected check <b>should fail
    */
   void method7()
   {
   }
   
   /**
    * Public check should fail</code>
    * should fail <
    */
   public void method8()
   {
   }
   
   /** {@inheritDoc} */
   public void method9()
   {
   }

    
    // Testcases to excercize the Tag parser (bug 843887)

    /**
     * Real men don't use XHTML.
     * <br />
     * <hr/>
     * < br/>
     * <img src="schattenparker.jpg"/></img>
     */
    private void method10()
    { // </img> should be the only error
    }

    /**
     * Tag content can be really mean.
     * <p>
     * Sometimes a p tag is closed.
     * </p>
     * <p>
     * Sometimes it's not.
     * 
     * <span style="font-family:'Times New Roman',Times,serif;font-size:200%">
     * Attributes can contain spaces and nested quotes.
     * </span>
     * <img src="slashesCanOccurWithin/attributes.jpg"/>
     * <img src="slashesCanOccurWithin/attributes.jpg">
     * <!-- comments <div> should not be checked. -->
     */
    private void method11()
    { // JavadocStyle should not report any error for this method
    }

    /**
     * Tags for two lines.
     * <a href="some_link"
     * >Link Text</a>
     */
    private void method12()
    {// JavadocStyle should not report any error for this method
    }

    /**
     * First sentence.
     * <pre>
     * +--LITERAL_DO (do)
     *     |
     *     +--SLIST ({)
     *         |
     *         +--EXPR
     *             |
     *             +--ASSIGN (=)
     *                 |
     *                 +--IDENT (x)
     *                 +--METHOD_CALL (()
     *                     |
     *                     +--DOT (.)
     *                         |
     *                         +--IDENT (rand)
     *                         +--IDENT (nextInt)
     *                     +--ELIST
     *                         |
     *                         +--EXPR
     *                             |
     *                             +--NUM_INT (10)
     *                     +--RPAREN ())
     *         +--SEMI (;)
     *         +--RCURLY (})
     *     +--LPAREN (()
     *     +--EXPR
     *         |
     *         +--LT (<)
     *             |
     *             +--IDENT (x)
     *             +--NUM_INT (5)
     *     +--RPAREN ())
     *     +--SEMI (;)
     * </pre>
     */
    private void method13()
    {// JavadocStyle should not report any error for this method
    }

    /**
     * Some problematic javadoc. Sample usage:
     * <blockquote>
     */

    private void method14()
    { // empty line between javadoc and method is critical (bug 841942)
    }

    /**
     * Empty line between javadoc and method declaration cause wrong
     * line number for reporting error (bug 841942)
     */

    private void method15()
    { // should report unended first sentance (check line number of the error)
    }
}
