package dev.sarek.agent.remove_final;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.pool.TypePool;

public class RemoveFinalPlugin implements Plugin {
  @Override
  public DynamicType.Builder<?> apply(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassFileLocator classFileLocator) {
    return builder
      .visit(new AsmVisitorWrapper() {
        @Override
        public int mergeWriter(int flags) {
          return 0;
        }

        @Override
        public int mergeReader(int flags) {
          return RemoveFinalTransformer.PARSING_FLAGS;
        }

        @Override
        public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor, Implementation.Context implementationContext, TypePool typePool, FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags, int readerFlags) {
          return new RemoveFinalTransformer(classVisitor, true);
        }
      });
  }

  @Override
  public void close() { }

  @Override
  public boolean matches(TypeDescription target) {
    return RemoveFinalTransformer.shouldTransform(target.getName());
  }
}
