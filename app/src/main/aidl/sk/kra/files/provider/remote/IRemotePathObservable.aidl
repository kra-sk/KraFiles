package sk.kra.files.provider.remote;

import sk.kra.files.provider.remote.ParcelableException;
import sk.kra.files.util.RemoteCallback;

interface IRemotePathObservable {
    void addObserver(in RemoteCallback observer);

    void close(out ParcelableException exception);
}
