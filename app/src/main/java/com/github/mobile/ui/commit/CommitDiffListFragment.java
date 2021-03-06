/*
 * Copyright 2012 GitHub Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mobile.ui.commit;

import static android.graphics.Paint.UNDERLINE_TEXT_FLAG;
import static com.github.mobile.Intents.EXTRA_BASE;
import static com.github.mobile.Intents.EXTRA_REPOSITORY;
import android.accounts.Account;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.mobile.R.id;
import com.github.mobile.R.string;
import com.github.mobile.core.commit.CommitUtils;
import com.github.mobile.core.commit.FullCommit;
import com.github.mobile.core.commit.FullCommitFile;
import com.github.mobile.core.commit.RefreshCommitTask;
import com.github.mobile.ui.DialogFragment;
import com.github.mobile.ui.HeaderFooterListAdapter;
import com.github.mobile.ui.StyledText;
import com.github.mobile.util.AvatarLoader;
import com.github.mobile.util.HttpImageGetter;
import com.github.mobile.util.ToastUtils;
import com.github.mobile.util.ViewUtils;
import com.google.inject.Inject;
import com.viewpagerindicator.R.layout;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.eclipse.egit.github.core.Commit;
import org.eclipse.egit.github.core.CommitComment;
import org.eclipse.egit.github.core.CommitFile;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryCommit;

import roboguice.inject.InjectExtra;
import roboguice.inject.InjectView;

/**
 * Fragment to display commit details with diff output
 */
public class CommitDiffListFragment extends DialogFragment implements
        OnItemClickListener {

    private DiffStyler diffStyler;

    @InjectView(android.R.id.list)
    private ListView list;

    @InjectView(id.pb_loading)
    private ProgressBar progress;

    @InjectExtra(EXTRA_REPOSITORY)
    private Repository repository;

    @InjectExtra(EXTRA_BASE)
    private String base;

    @Inject
    private AvatarLoader avatars;

    private View commitHeader;

    private TextView commitMessage;

    private View authorArea;

    private ImageView authorAvatar;

    private TextView authorName;

    private TextView authorDate;

    private View committerArea;

    private ImageView committerAvatar;

    private TextView committerName;

    private TextView committerDate;

    private HeaderFooterListAdapter<CommitFileListAdapter> adapter;

    private HttpImageGetter commentImageGetter;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        commentImageGetter = new HttpImageGetter(getActivity());

        refreshCommit();
    }

    private void refreshCommit() {
        new RefreshCommitTask(getActivity(), repository, base,
                commentImageGetter) {

            @Override
            protected FullCommit run(Account account) throws Exception {
                FullCommit full = super.run(account);

                List<CommitFile> files = full.getCommit().getFiles();
                diffStyler.setFiles(files);
                if (files != null)
                    Collections.sort(files, new CommitFileComparator());
                return full;
            }

            @Override
            protected void onSuccess(FullCommit commit) throws Exception {
                super.onSuccess(commit);

                updateList(commit);
            }

            @Override
            protected void onException(Exception e) throws RuntimeException {
                super.onException(e);

                ToastUtils.show(getActivity(), e, string.error_commit_load);
            }

        }.execute();
    }

    private boolean isDifferentCommitter(final String author,
            final Date authorDate, final String committer,
            final Date committerDate) {
        if (committer == null)
            return false;
        if (!committer.equals(author))
            return true;
        return committerDate != null && !committerDate.equals(authorDate);
    }

    private void addCommitDetails(RepositoryCommit commit) {
        adapter.addHeader(commitHeader);

        String commitAuthor = CommitUtils.getAuthor(commit);
        Date commitAuthorDate = CommitUtils.getAuthorDate(commit);
        String commitCommitter = CommitUtils.getCommitter(commit);
        Date commitCommitterDate = CommitUtils.getCommiterDate(commit);

        commitMessage.setText(commit.getCommit().getMessage());

        if (commitAuthor != null) {
            CommitUtils.bindAuthor(commit, avatars, authorAvatar);
            authorName.setText(commitAuthor);
            StyledText styledAuthor = new StyledText();
            styledAuthor.append(getString(string.authored));
            if (commitAuthorDate != null)
                styledAuthor.append(' ').append(commitAuthorDate);
            authorDate.setText(styledAuthor);
            ViewUtils.setGone(authorArea, false);
        } else
            ViewUtils.setGone(authorArea, true);

        if (isDifferentCommitter(commitAuthor, commitAuthorDate,
                commitCommitter, commitCommitterDate)) {
            CommitUtils.bindCommitter(commit, avatars, committerAvatar);
            committerName.setText(commitCommitter);
            StyledText styledCommitter = new StyledText();
            styledCommitter.append(getString(string.committed));
            if (commitCommitterDate != null)
                styledCommitter.append(' ').append(commitCommitterDate);
            committerDate.setText(styledCommitter);
            ViewUtils.setGone(committerArea, false);
        } else
            ViewUtils.setGone(committerArea, true);
    }

    private void addDiffStats(RepositoryCommit commit, LayoutInflater inflater) {
        View fileHeader = inflater.inflate(layout.commit_file_details_header,
                null);
        ((TextView) fileHeader.findViewById(id.tv_commit_file_summary))
                .setText(CommitUtils.formatStats(commit.getFiles()));
        adapter.addHeader(fileHeader);
    }

    private void addCommitParents(RepositoryCommit commit,
            LayoutInflater inflater) {
        List<Commit> parents = commit.getParents();
        if (parents == null || parents.isEmpty())
            return;

        for (Commit parent : parents) {
            View parentView = inflater.inflate(layout.commit_parent_item, null);
            TextView parentIdText = (TextView) parentView
                    .findViewById(id.tv_commit_id);
            parentIdText.setPaintFlags(parentIdText.getPaintFlags()
                    | UNDERLINE_TEXT_FLAG);
            StyledText parentText = new StyledText();
            parentText.append(getString(string.parent_prefix));
            parentText.monospace(CommitUtils.abbreviate(parent));
            parentIdText.setText(parentText);
            adapter.addHeader(parentView, parent, true);
        }
    }

    private void updateList(FullCommit fullCommit) {
        if (!isUsable())
            return;

        RepositoryCommit commit = fullCommit.getCommit();
        LayoutInflater inflater = getActivity().getLayoutInflater();

        ViewUtils.setGone(progress, true);
        ViewUtils.setGone(list, false);

        adapter.clearHeaders();

        addCommitDetails(commit);
        addCommitParents(commit, inflater);
        addDiffStats(commit, inflater);

        CommitFileListAdapter rootAdapter = adapter.getWrappedAdapter();
        for (FullCommitFile file : fullCommit.getFiles())
            rootAdapter.addItem(file);
        for (CommitComment comment : fullCommit)
            rootAdapter.addComment(comment);

        adapter.addFooter(inflater.inflate(layout.footer_separator, null));
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        diffStyler = new DiffStyler(getResources());

        list.setOnItemClickListener(this);

        LayoutInflater inflater = getActivity().getLayoutInflater();

        adapter = new HeaderFooterListAdapter<CommitFileListAdapter>(list,
                new CommitFileListAdapter(inflater, diffStyler, avatars,
                        new HttpImageGetter(getActivity())));
        list.setAdapter(adapter);

        commitHeader = inflater.inflate(layout.commit_header, null);
        commitMessage = (TextView) commitHeader
                .findViewById(id.tv_commit_message);

        authorArea = commitHeader.findViewById(id.ll_author);
        authorAvatar = (ImageView) commitHeader.findViewById(id.iv_author);
        authorName = (TextView) commitHeader.findViewById(id.tv_author);
        authorDate = (TextView) commitHeader.findViewById(id.tv_author_date);

        committerArea = commitHeader.findViewById(id.ll_committer);
        committerAvatar = (ImageView) commitHeader
                .findViewById(id.iv_committer);
        committerName = (TextView) commitHeader.findViewById(id.tv_committer);
        committerDate = (TextView) commitHeader.findViewById(id.tv_commit_date);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(layout.commit_diff_list, container);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        Object item = parent.getItemAtPosition(position);
        if (item instanceof Commit)
            startActivity(CommitViewActivity.createIntent(repository,
                    ((Commit) item).getSha()));
        else if (item instanceof CommitFile)
            startActivity(CommitFileViewActivity.createIntent(repository, base,
                    (CommitFile) item));
    }
}
