package com.tyron.builder.api.internal.tasks;

import com.tyron.builder.internal.operations.BuildOperationType;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the computation of the task artifact state and the task output caching state.
 * <p>
 * This operation is executed only when the build cache is enabled or when the build scan plugin is applied.
 * Must occur as a child of {@link com.tyron.builder.api.internal.tasks.execution.ExecuteTaskBuildOperationType}.
 *
 * @since 4.0
 */
public final class SnapshotTaskInputsBuildOperationType implements BuildOperationType<SnapshotTaskInputsBuildOperationType.Details, SnapshotTaskInputsBuildOperationType.Result> {

//    @UsedByScanPlugin
    public interface Details {
    }

    /**
     * The hashes of the inputs.
     * <p>
     * If the inputs were not snapshotted, all fields are null.
     * This may occur if the task had no outputs.
     */
//    @UsedByScanPlugin
    public interface Result {

        /**
         * The overall hash value for the inputs.
         * <p>
         * Null if the overall key was not calculated because the inputs were invalid.
         */
        @Nullable
        byte[] getHashBytes();

        /**
         * The hash of the classloader that loaded the task implementation.
         * <p>
         * Null if the classloader is not managed by Gradle.
         */
        @Nullable
        byte[] getClassLoaderHashBytes();


        /**
         * The hashes of the classloader that loaded each of the task's actions.
         * <p>
         * May contain duplicates.
         * Order corresponds to execution order of the actions.
         * Never empty.
         * May contain nulls (non Gradle managed classloader)
         */
        @Nullable
        List<byte[]> getActionClassLoaderHashesBytes();

        /**
         * The class names of each of the task's actions.
         * <p>
         * May contain duplicates.
         * Order corresponds to execution order of the actions.
         * Never empty.
         */
        @Nullable
        List<String> getActionClassNames();

        @Nullable
        Map<String, byte[]> getInputValueHashesBytes();

        /**
         * The consuming visitor for file property inputs.
         * <p>
         * Properties are visited depth-first lexicographical.
         * Roots are visited in semantic order (i.e. the order in which they make up the file collection)
         * Files and directories are depth-first lexicographical.
         * <p>
         * For roots that are a file, they are also visited with {@link #file(VisitState)}.
         */
        interface InputFilePropertyVisitor {

            /**
             * Called once per file property.
             * <p>
             * Only getProperty*() state methods may be called during.
             */
            void preProperty(VisitState state);

            /**
             * Called for each root of the current property.
             * <p>
             * {@link VisitState#getName()} and {@link VisitState#getPath()} may be called during.
             */
            void preRoot(VisitState state);

            /**
             * Called before entering a directory.
             * <p>
             * {@link VisitState#getName()} and {@link VisitState#getPath()} may be called during.
             */
            void preDirectory(VisitState state);

            /**
             * Called when visiting a non-directory file.
             * <p>
             * {@link VisitState#getName()}, {@link VisitState#getPath()} and {@link VisitState#getHashBytes()} may be called during.
             */
            void file(VisitState state);

            /**
             * Called when exiting a directory.
             */
            void postDirectory();

            /**
             * Called when exiting a root.
             */
            void postRoot();

            /**
             * Called when exiting a property.
             */
            void postProperty();
        }

        /**
         * Provides information about the current location in the visit.
         * <p>
         * Consumers should expect this to be mutable.
         * Calling any method on this outside of a method that received it has undefined behavior.
         */
        interface VisitState {
            /**
             * Returns the currently visited property name. Each property has a unique name.
             */
            String getPropertyName();

            /**
             * Returns the hash of the currently visited property.
             */
            byte[] getPropertyHashBytes();

            /**
             * The “primary” attribute of the current property.
             *
             * Used by Gradle Enterprise plugin < 3.8, retained for backwards compatibility.
             *
             * Returns the name value of one of:
             *
             * <li>com.tyron.builder.internal.fingerprint.FingerprintingStrategy#CLASSPATH_IDENTIFIER</li>
             * <li>com.tyron.builder.internal.fingerprint.FingerprintingStrategy#COMPILE_CLASSPATH_IDENTIFIER</li>
             * <li>com.tyron.builder.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy#IDENTIFIER</li>
             * <li>com.tyron.builder.internal.fingerprint.impl.RelativePathFingerprintingStrategy#IDENTIFIER</li>
             * <li>com.tyron.builder.internal.fingerprint.impl.NameOnlyFingerprintingStrategy#IDENTIFIER</li>
             * <li>com.tyron.builder.internal.fingerprint.impl.IgnoredPathFingerprintingStrategy#IDENTIFIER</li>
             *
             * @deprecated since 7.3, superseded by {@link #getPropertyAttributes()}
             */
            @Deprecated
            String getPropertyNormalizationStrategyName();

            /**
             * A description of how the current property was fingerprinted.
             *
             * Returns one or more of the values of com.tyron.builder.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute, sorted.
             *
             * This interface does not constrain the compatibility of values.
             * In practice however, such constraints do exist but are managed informally.
             * For example, consumers can assume that both com.tyron.builder.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute#DIRECTORY_SENSITIVITY_DEFAULT
             * and com.tyron.builder.api.internal.tasks.SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute#DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES will not be present.
             * This loose approach is used to allow the various types of normalization supported by Gradle to evolve,
             * and their usage to be conveyed here without changing this interface.
             *
             * @since 7.3
             */
            Set<String> getPropertyAttributes();

            /**
             * Returns the absolute path of the currently visited location.
             */
            String getPath();

            /**
             * Returns the name of the currently visited location, as in {@link File#getName()}
             */
            String getName();

            /**
             * Returns the normalized content hash of the last visited file.
             * <p>
             * Must not be called when the last visited location was a directory.
             */
            byte[] getHashBytes();
        }

        /**
         * Traverses the input properties that are file types (e.g. File, FileCollection, FileTree, List of File).
         * <p>
         * If there are no input file properties, visitor will not be called at all.
         */
        void visitInputFileProperties(InputFilePropertyVisitor visitor);

        /**
         * Names of input properties which have been loaded by non Gradle managed classloader.
         * <p>
         * Ordered by property name, lexicographically.
         * No null values.
         * Never empty.
         *
         * This is kept for backward compatibility with the Gradle Enterprise Gradle plugin.
         *
         * @deprecated Always null, since we don't capture inputs when anything is loaded by an unknown classloader.
         */
        @Deprecated
        @Nullable
        Set<String> getInputPropertiesLoadedByUnknownClassLoader();

        /**
         * The names of the output properties.
         * <p>
         * No duplicate values.
         * Ordered lexicographically.
         * Never empty.
         */
        @Nullable
        List<String> getOutputPropertyNames();

    }

    private SnapshotTaskInputsBuildOperationType() {
    }

}
