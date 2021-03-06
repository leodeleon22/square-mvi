package com.leodeleon.domain.states

import com.leodeleon.domain.repositories.ILocalRepository
import com.leodeleon.domain.repositories.IRemoteRepository
import io.reactivex.Observable
import java.util.concurrent.TimeUnit

class DetailsStateMachine(remote: IRemoteRepository, local: ILocalRepository) : BaseStateMachine() {

    override val initialState = EmptyState

    override fun reducer(state: State, action: Action): State {
        return when (action) {
            is ShowError -> ErrorState(action.message)
            is ShowRepo -> ShowRepoState(action.repo, action.isBookmarked)
            is BookmarkUpdated -> BookmarkState(action.isBookmarked)
            is UsersLoaded -> ShowUsersState(action.users)
            is ShowSnack -> SnackState(true, action.message)
            is HideSnack -> SnackState(false)
            else -> state
        }
    }

    override val effects= sideEffects {
        sideEffect<ShowRepo> {
            it.flatMap { action ->
                remote.getStargazers(action.repo.name)
                        .map<Action> { UsersLoaded(it) }
            }.onErrorReturn { ShowError("Error loading users") }
        }

        sideEffect<UpdateBookmark> {
            it.flatMap<Action> { action ->
                val bookmark = !action.isBookmarked
                val result = if (bookmark) local.saveBookmark(action.repo) else local.removeBookmark(action.repo)
                result.andThen(Observable.just(bookmark))
                        .map { BookmarkUpdated(it) }
            }
        }

        sideEffect<BookmarkUpdated> {
            it.switchMap { action ->
                Observable.timer(3, TimeUnit.SECONDS)
                        .map<Action> { HideSnack }
                        .startWith(ShowSnack(
                                if (action.isBookmarked) "Repository saved to bookmarks"
                                else "Repository removed from bookmarks"
                        ))
                        .onErrorReturn {
                            ShowSnack("Error saving bookmark!")
                        }
            }
        }
    }
}
