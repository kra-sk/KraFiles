/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sk.kra.files.provider.common

import java.io.Closeable

interface CloseableIterator<T> : Iterator<T>, Closeable
