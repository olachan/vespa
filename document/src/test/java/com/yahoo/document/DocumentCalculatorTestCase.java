// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;

import java.util.HashMap;

/**
 * @author thomasg
 */
public class DocumentCalculatorTestCase extends junit.framework.TestCase {

    DocumentType testDocType = null;
    DocumentTypeManager docMan;
    Document doc;

    public void setUp() {
        docMan = new DocumentTypeManager();
        testDocType = new DocumentType("testdoc");

        testDocType.addHeaderField("byteattr", DataType.BYTE);
        testDocType.addHeaderField("intattr", DataType.INT);
        testDocType.addHeaderField("longattr", DataType.LONG);
        testDocType.addHeaderField("doubleattr", DataType.DOUBLE);
        testDocType.addHeaderField("missingattr", DataType.INT);

        docMan.registerDocumentType(testDocType);
        doc = new Document(testDocType, new DocumentId("doc:testdoc:http://www.ntnu.no/"));
        doc.setFieldValue(testDocType.getField("byteattr"), new ByteFieldValue((byte)32));
        doc.setFieldValue(testDocType.getField("intattr"), new IntegerFieldValue(468));
        doc.setFieldValue(testDocType.getField("longattr"), new LongFieldValue((long)327));
        doc.setFieldValue(testDocType.getField("doubleattr"), new DoubleFieldValue(25.0));
    }

    public void testConstant() throws Exception {
        DocumentCalculator calculator = new DocumentCalculator("4.0");
        assertEquals(4.0, calculator.evaluate(doc, new HashMap<String, Object>()));
    }

    public void testSimple() throws Exception {
        DocumentCalculator calculator = new DocumentCalculator("(3 + 5) / 2");
        assertEquals(4.0, calculator.evaluate(doc, new HashMap<String, Object>()));
    }

    public void testDivideByZero() throws Exception {
        DocumentCalculator calculator = new DocumentCalculator("(3 + 5) / 0");
        try {
            System.out.println(calculator.evaluate(doc, new HashMap<String, Object>()));
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testModByZero() throws Exception {
        DocumentCalculator calculator = new DocumentCalculator("(3 + 5) % 0");
        try {
            System.out.println(calculator.evaluate(doc, new HashMap<String, Object>()));
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testFieldDivideByZero() throws Exception {
        try {
            DocumentCalculator calculator = new DocumentCalculator("(testdoc.byteattr + testdoc.intattr) / testdoc.doubleattr");
            doc.setFieldValue("doubleattr", new DoubleFieldValue(0.0));
            calculator.evaluate(doc, new HashMap<String, Object>());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testVariables() throws Exception {
        HashMap<String, Object> vars = new HashMap<String, Object>();
        vars.put("x", new Double(3.0));
        vars.put("y", new Double(5.0));
        DocumentCalculator calculator = new DocumentCalculator("($x + $y) / 2");
        assertEquals(4.0, calculator.evaluate(doc, vars));
    }

    public void testFields() throws Exception {
        DocumentCalculator calculator = new DocumentCalculator("(testdoc.byteattr + testdoc.intattr) / testdoc.doubleattr");
        assertEquals(20.0, calculator.evaluate(doc, new HashMap<String, Object>()));
    }

    public void testMissingField() throws Exception {
        try {
            DocumentCalculator calculator = new DocumentCalculator("(testdoc.nosuchattribute + testdoc.intattr) / testdoc.doubleattr");
            calculator.evaluate(doc, new HashMap<String, Object>());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testFieldNotSet() throws Exception {
        try {
            DocumentCalculator calculator = new DocumentCalculator("(testdoc.missingattr + testdoc.intattr) / testdoc.doubleattr");
            calculator.evaluate(doc, new HashMap<String, Object>());
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

}
