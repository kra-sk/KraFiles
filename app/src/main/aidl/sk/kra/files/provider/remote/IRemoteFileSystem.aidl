package sk.kra.files.provider.remote;

import sk.kra.files.provider.remote.ParcelableException;

interface IRemoteFileSystem {
    void close(out ParcelableException exception);
}
