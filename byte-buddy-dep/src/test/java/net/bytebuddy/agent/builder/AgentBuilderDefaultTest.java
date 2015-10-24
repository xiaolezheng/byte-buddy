package net.bytebuddy.agent.builder;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.inline.MethodRebaseResolver;
import net.bytebuddy.implementation.LoadedTypeInitializer;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.test.utility.MockitoRule;
import net.bytebuddy.test.utility.ObjectPropertyAssertion;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.AccessControlContext;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

public class AgentBuilderDefaultTest {

    private static final byte[] QUX = new byte[]{1, 2, 3}, BAZ = new byte[]{4, 5, 6};

    private static final Class<?> REDEFINED = Foo.class;

    @Rule
    public TestRule mockitoRule = new MockitoRule(this);

    @Mock
    private Instrumentation instrumentation;

    @Mock
    private ByteBuddy byteBuddy;

    @Mock
    private DynamicType.Builder<?> builder;

    @Mock
    private DynamicType.Unloaded<?> unloaded;

    @Mock
    private LoadedTypeInitializer loadedTypeInitializer;

    @Mock
    private AgentBuilder.RawMatcher rawMatcher;

    @Mock
    private AgentBuilder.Transformer transformer;

    @Mock
    private AgentBuilder.BinaryLocator binaryLocator;

    @Mock
    private AgentBuilder.TypeStrategy typeStrategy;

    @Mock
    private TypePool typePool;

    @Mock
    private ClassFileLocator classFileLocator;

    @Mock
    private TypePool.Resolution resolution;

    @Mock
    private AgentBuilder.Listener listener;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        when(builder.make()).thenReturn((DynamicType.Unloaded) unloaded);
        when(unloaded.getTypeDescription()).thenReturn(new TypeDescription.ForLoadedType(REDEFINED));
        when(typeStrategy.builder(any(TypeDescription.class),
                eq(byteBuddy),
                any(ClassFileLocator.class),
                any(MethodRebaseResolver.MethodNameTransformer.class))).thenReturn((DynamicType.Builder) builder);
        Map<TypeDescription, LoadedTypeInitializer> loadedTypeInitializers = new HashMap<TypeDescription, LoadedTypeInitializer>();
        loadedTypeInitializers.put(new TypeDescription.ForLoadedType(REDEFINED), loadedTypeInitializer);
        when(unloaded.getLoadedTypeInitializers()).thenReturn(loadedTypeInitializers);
        when(unloaded.getBytes()).thenReturn(BAZ);
        when(transformer.transform(builder, new TypeDescription.ForLoadedType(REDEFINED))).thenReturn((DynamicType.Builder) builder);
        when(binaryLocator.classFileLocator(REDEFINED.getClassLoader())).thenReturn(classFileLocator);
        when(binaryLocator.typePool(any(ClassFileLocator.class))).thenReturn(typePool);
        when(typePool.describe(REDEFINED.getName())).thenReturn(resolution);
        when(instrumentation.getAllLoadedClasses()).thenReturn(new Class<?>[]{REDEFINED});
    }

    @Test
    public void testSuccessfulWithoutExistingClass() throws Exception {
        when(unloaded.getBytes()).thenReturn(BAZ);
        when(resolution.resolve()).thenReturn(new TypeDescription.ForLoadedType(REDEFINED));
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), null, REDEFINED.getProtectionDomain()))
                .thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        assertThat(classFileTransformer.transform(REDEFINED.getClassLoader(), REDEFINED.getName(), null, REDEFINED.getProtectionDomain(), QUX), is(BAZ));
        verify(listener).onTransformation(new TypeDescription.ForLoadedType(REDEFINED), unloaded);
        verify(listener).onComplete(REDEFINED.getName());
        verifyNoMoreInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, false);
        verifyNoMoreInteractions(instrumentation);
    }

    @Test
    public void testSuccessfulWithExistingClass() throws Exception {
        when(unloaded.getBytes()).thenReturn(BAZ);
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain()))
                .thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        assertThat(classFileTransformer.transform(REDEFINED.getClassLoader(), REDEFINED.getName(), REDEFINED, REDEFINED.getProtectionDomain(), QUX), is(BAZ));
        verify(listener).onTransformation(new TypeDescription.ForLoadedType(REDEFINED), unloaded);
        verify(listener).onComplete(REDEFINED.getName());
        verifyNoMoreInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, false);
        verifyNoMoreInteractions(instrumentation);
    }

    @Test
    public void testSkipRetransformationWithNonRedefinable() throws Exception {
        when(unloaded.getBytes()).thenReturn(BAZ);
        when(resolution.resolve()).thenReturn(new TypeDescription.ForLoadedType(REDEFINED));
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain())).thenReturn(true);
        when(instrumentation.isModifiableClass(REDEFINED)).thenReturn(false);
        when(instrumentation.isRetransformClassesSupported()).thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withRedefinitionStrategy(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        verifyZeroInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, true);
        verify(instrumentation).isModifiableClass(REDEFINED);
        verify(instrumentation).getAllLoadedClasses();
        verify(instrumentation).isRetransformClassesSupported();
        verifyNoMoreInteractions(instrumentation);
        verifyZeroInteractions(rawMatcher);
    }

    @Test
    public void testSkipRetransformationWithNonMatched() throws Exception {
        when(unloaded.getBytes()).thenReturn(BAZ);
        when(resolution.resolve()).thenReturn(new TypeDescription.ForLoadedType(REDEFINED));
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain()))
                .thenReturn(false);
        when(instrumentation.isModifiableClass(REDEFINED)).thenReturn(true);
        when(instrumentation.isRetransformClassesSupported()).thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withRedefinitionStrategy(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        verifyZeroInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, true);
        verify(instrumentation).isModifiableClass(REDEFINED);
        verify(instrumentation).getAllLoadedClasses();
        verify(instrumentation).isRetransformClassesSupported();
        verifyNoMoreInteractions(instrumentation);
        verify(rawMatcher).matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain());
        verifyNoMoreInteractions(rawMatcher);
    }

    @Test
    public void testSuccessfulWithRetransformationMatched() throws Exception {
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain())).thenReturn(true);
        when(instrumentation.isModifiableClass(REDEFINED)).thenReturn(true);
        when(instrumentation.isRetransformClassesSupported()).thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withRedefinitionStrategy(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        verifyZeroInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, true);
        verify(instrumentation).getAllLoadedClasses();
        verify(instrumentation).isModifiableClass(REDEFINED);
        verify(instrumentation).retransformClasses(REDEFINED);
        verify(instrumentation).isRetransformClassesSupported();
        verifyNoMoreInteractions(instrumentation);
        verify(rawMatcher).matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain());
        verifyNoMoreInteractions(rawMatcher);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRetransformationNotSupported() throws Exception {
        new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withRedefinitionStrategy(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
    }

    @Test
    public void testSkipRedefinitionWithNonRedefinable() throws Exception {
        when(unloaded.getBytes()).thenReturn(BAZ);
        when(resolution.resolve()).thenReturn(new TypeDescription.ForLoadedType(REDEFINED));
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain()))
                .thenReturn(true);
        when(instrumentation.isModifiableClass(REDEFINED)).thenReturn(false);
        when(instrumentation.isRedefineClassesSupported()).thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withRedefinitionStrategy(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        verifyZeroInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, false);
        verify(instrumentation).isModifiableClass(REDEFINED);
        verify(instrumentation).getAllLoadedClasses();
        verify(instrumentation).isRedefineClassesSupported();
        verifyNoMoreInteractions(instrumentation);
        verifyZeroInteractions(rawMatcher);
    }

    @Test
    public void testSkipRedefinitionWithNonMatched() throws Exception {
        when(unloaded.getBytes()).thenReturn(BAZ);
        when(resolution.resolve()).thenReturn(new TypeDescription.ForLoadedType(REDEFINED));
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain()))
                .thenReturn(false);
        when(instrumentation.isModifiableClass(REDEFINED)).thenReturn(true);
        when(instrumentation.isRedefineClassesSupported()).thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withRedefinitionStrategy(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        verifyZeroInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, false);
        verify(instrumentation).isModifiableClass(REDEFINED);
        verify(instrumentation).getAllLoadedClasses();
        verify(instrumentation).isRedefineClassesSupported();
        verifyNoMoreInteractions(instrumentation);
        verify(rawMatcher).matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain());
        verifyNoMoreInteractions(rawMatcher);
    }

    @Test
    public void testSuccessfulWithRedefinitionMatched() throws Exception {
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain()))
                .thenReturn(true);
        when(instrumentation.isModifiableClass(REDEFINED)).thenReturn(true);
        when(instrumentation.isRedefineClassesSupported()).thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withRedefinitionStrategy(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        verify(listener).onTransformation(new TypeDescription.ForLoadedType(REDEFINED), unloaded);
        verify(listener).onComplete(REDEFINED.getName());
        verifyNoMoreInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, false);
        verify(instrumentation).getAllLoadedClasses();
        verify(instrumentation).isModifiableClass(REDEFINED);
        verify(instrumentation).redefineClasses(any(ClassDefinition[].class));
        verify(instrumentation).isRedefineClassesSupported();
        verifyNoMoreInteractions(instrumentation);
        verify(rawMatcher).matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain());
        verifyNoMoreInteractions(rawMatcher);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRedefinitionNotSupported() throws Exception {
        new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withRedefinitionStrategy(AgentBuilder.RedefinitionStrategy.REDEFINITION)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
    }

    @Test
    public void testTransformationWithError() throws Exception {
        when(unloaded.getBytes()).thenReturn(BAZ);
        RuntimeException exception = mock(RuntimeException.class);
        when(resolution.resolve()).thenThrow(exception);
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain()))
                .thenReturn(true);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        assertThat(classFileTransformer.transform(REDEFINED.getClassLoader(), REDEFINED.getName(), null, REDEFINED.getProtectionDomain(), QUX),
                nullValue(byte[].class));
        verify(listener).onError(REDEFINED.getName(), exception);
        verify(listener).onComplete(REDEFINED.getName());
        verifyNoMoreInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, false);
        verifyNoMoreInteractions(instrumentation);
    }

    @Test
    public void testIgnored() throws Exception {
        when(unloaded.getBytes()).thenReturn(BAZ);
        when(resolution.resolve()).thenReturn(new TypeDescription.ForLoadedType(REDEFINED));
        when(rawMatcher.matches(new TypeDescription.ForLoadedType(REDEFINED), REDEFINED.getClassLoader(), REDEFINED, REDEFINED.getProtectionDomain()))
                .thenReturn(false);
        ClassFileTransformer classFileTransformer = new AgentBuilder.Default(byteBuddy)
                .withInitialization(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .withBinaryLocator(binaryLocator)
                .withTypeStrategy(typeStrategy)
                .withListener(listener)
                .withoutNativeMethodPrefix()
                .type(rawMatcher).transform(transformer)
                .installOn(instrumentation);
        assertThat(classFileTransformer.transform(REDEFINED.getClassLoader(), REDEFINED.getName(), REDEFINED, REDEFINED.getProtectionDomain(), QUX),
                nullValue(byte[].class));
        verify(listener).onIgnored(new TypeDescription.ForLoadedType(REDEFINED));
        verify(listener).onComplete(REDEFINED.getName());
        verifyNoMoreInteractions(listener);
        verify(instrumentation).addTransformer(classFileTransformer, false);
        verifyNoMoreInteractions(instrumentation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPrefixThrowsException() throws Exception {
        new AgentBuilder.Default(byteBuddy).withNativeMethodPrefix("");
    }

    @Test
    public void testObjectProperties() throws Exception {
        ObjectPropertyAssertion.of(AgentBuilder.Default.class).create(new ObjectPropertyAssertion.Creator<AccessControlContext>() {
            @Override
            public AccessControlContext create() {
                return new AccessControlContext(new ProtectionDomain[]{mock(ProtectionDomain.class)});
            }
        }).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.Matched.class).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.Transformation.Simple.class).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.Transformation.Simple.Resolution.class).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.Transformation.Ignored.class).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.Transformation.Compound.class).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.Transformation.Resolution.Unresolved.class).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.BootstrapInjectionStrategy.Enabled.class).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.BootstrapInjectionStrategy.Disabled.class).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.ExecutingTransformer.class).create(new ObjectPropertyAssertion.Creator<AccessControlContext>() {
            @Override
            public AccessControlContext create() {
                return new AccessControlContext(new ProtectionDomain[]{mock(ProtectionDomain.class)});
            }
        }).apply();
        ObjectPropertyAssertion.of(AgentBuilder.Default.ExecutingTransformer.InstrumentedTypeLocator.class).apply();
        final Iterator<Class<?>> iterator = Arrays.<Class<?>>asList(Object.class, AgentBuilderDefaultTest.class).iterator();
        ObjectPropertyAssertion.of(AgentBuilder.Default.InitializationStrategy.SelfInjection.Nexus.class).create(new ObjectPropertyAssertion.Creator<Class<?>>() {
            @Override
            public Class<?> create() {
                return iterator.next();
            }
        }).apply();
    }

    public static class Foo {

    }
}