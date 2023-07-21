/**
 * Copyright (C) 2023 Data Cooker Team and Contributors
 * This project uses New BSD license with do no evil clause. For full text, check the LICENSE file in the root directory.
 */
package io.github.pastorgl.datacooker.storage.s3direct;

import io.github.pastorgl.datacooker.config.InvalidConfigurationException;
import io.github.pastorgl.datacooker.data.StreamType;
import io.github.pastorgl.datacooker.metadata.DefinitionMetaBuilder;
import io.github.pastorgl.datacooker.metadata.OutputAdapterMeta;
import io.github.pastorgl.datacooker.storage.hadoop.HadoopStorage;
import io.github.pastorgl.datacooker.storage.hadoop.functions.PartOutputFunction;
import io.github.pastorgl.datacooker.storage.s3direct.functions.S3DirectTextOutputFunction;

import static io.github.pastorgl.datacooker.storage.hadoop.HadoopStorage.*;
import static io.github.pastorgl.datacooker.storage.s3direct.S3DirectStorage.*;

@SuppressWarnings("unused")
public abstract class S3DirectTextOutput extends S3DirectOutput {
    protected String[] columns;
    protected String delimiter;

    @Override
    public OutputAdapterMeta meta() {
        return new OutputAdapterMeta("s3direct", "Multipart output adapter for any S3-compatible storage," +
                " based on Hadoop Delimited Text adapter.",
                new String[]{"s3d://bucket/prefix/to/output/csv/files/"},

                new StreamType[]{StreamType.PlainText, StreamType.Columnar},
                new DefinitionMetaBuilder()
                        .def(CODEC, "Codec to compress the output", HadoopStorage.Codec.class, HadoopStorage.Codec.NONE,
                                "By default, use no compression")
                        .def(S3D_ACCESS_KEY, "S3 access key", null, "By default, try to discover" +
                                " the key from client's standard credentials chain")
                        .def(S3D_SECRET_KEY, "S3 secret key", null, "By default, try to discover" +
                                " the key from client's standard credentials chain")
                        .def(S3D_ENDPOINT, "S3 endpoint", null, "By default, try to discover" +
                                " the endpoint from client's standard profile")
                        .def(S3D_REGION, "S3 region", null, "By default, try to discover" +
                                " the region from client's standard profile")
                        .def(CONTENT_TYPE, "Content type for objects", "text/csv", "By default," +
                                " content type is CSV")
                        .def(COLUMNS, "Columns to write",
                                String[].class, null, "By default, select all columns")
                        .def(DELIMITER, "Record column delimiter",
                                String.class, "\t", "By default, tabulation character")
                        .build()
        );
    }

    @Override
    protected void configure() throws InvalidConfigurationException {
        super.configure();

        columns = resolver.get(COLUMNS);
        delimiter = resolver.get(DELIMITER);
    }

    @Override
    protected PartOutputFunction getOutputFunction(String sub) {
        return new S3DirectTextOutputFunction(sub, path, codec, columns, delimiter.charAt(0), endpoint, region, accessKey, secretKey, contentType);
    }
}
