/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 artipie.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.npm.http;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rs.StandardRs;
import com.artipie.npm.PackageNameFromUrl;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.reactivestreams.Publisher;

/**
 * Slice to handle `npm unpubslish` command requests.
 * Request line to this slice looks like `/[<@scope>/]pkg/-rev/undefined`.
 * @since 0.8
 */
public final class UnpublishForceSlice implements Slice {
    /**
     * Endpoint request line pattern.
     */
    static final Pattern PTRN = Pattern.compile("/.*/-rev/.*$");

    /**
     * Abstract Storage.
     */
    private final Storage storage;

    /**
     * Ctor.
     * @param storage Abstract storage
     */
    public UnpublishForceSlice(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public Response response(
        final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body
    ) {
        final RequestLineFrom rqline = new RequestLineFrom(line);
        final String uri = rqline.uri().getPath();
        final Matcher matcher = UnpublishForceSlice.PTRN.matcher(uri);
        final Response resp;
        if (matcher.matches()) {
            final String pkg = new PackageNameFromUrl(
                String.format(
                    "%s %s %s", rqline.method(),
                    uri.substring(0, uri.indexOf("/-rev/")),
                    rqline.version()
                )
            ).value();
            resp = new AsyncResponse(
                this.storage.list(new Key.From(pkg))
                    .thenCompose(
                        keys -> CompletableFuture.allOf(
                            keys.stream()
                                .map(this.storage::delete)
                                .toArray(CompletableFuture[]::new)
                        ).thenApply(nothing -> StandardRs.OK)
                    )
            );
        } else {
            resp = new RsWithStatus(RsStatus.BAD_REQUEST);
        }
        return resp;
    }
}
