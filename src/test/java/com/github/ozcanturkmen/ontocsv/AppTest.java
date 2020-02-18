package com.github.ozcanturkmen.ontocsv;

import org.junit.Assert;
import org.junit.Test;
// import org.junit.runner.RunWith;
// import org.junit.runners.Parameterized;

/**
 * Unit test for simple App.
 */
public class AppTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentException() {
        System.out.println("Testing shouldThrowIllegalArgumentException...");
        String className = "classA";
        String instanceName = "      ";
        new ClassInstance(className, instanceName);
    }

    @Test
    public void shouldNormalizeInstanceName() {
        ClassInstance instance = new ClassInstance("myClass","(\"maven\"&&& or =/-    good.old..'gradle')");
        System.out.println("instanceName BEFORE normalization: " + instance.getInstanceName());
        String normalized = instance.getNormalizedInstanceName();
        System.out.println("instanceName AFTER normalization: " + normalized);
        Assert.assertEquals("_maven_and_or_good_old_gradle_", normalized);
        instance.setInstanceName(" COLOR (both 'cyan' && red)");
        System.out.println("instanceName BEFORE normalization: " + instance.getInstanceName());
        normalized = instance.getNormalizedInstanceName();
        Assert.assertEquals("color_both_cyan__and_red", normalized);
        System.out.println("instanceName AFTER normalization: " + instance.getNormalizedInstanceName());
        instance.setInstanceName("a --- -b");
        Assert.assertEquals("a__b", instance.getNormalizedInstanceName());
    }
}
