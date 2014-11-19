/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractSchemesManager<T extends Scheme, E extends ExternalizableScheme> implements SchemesManager<T, E> {
  private static final Logger LOG = Logger.getInstance(AbstractSchemesManager.class);

  protected final List<T> mySchemes = new ArrayList<T>();
  private volatile T myCurrentScheme;
  private String myCurrentSchemeName;

  @Override
  public void addNewScheme(@NotNull T scheme, boolean replaceExisting) {
    int toReplace = -1;
    for (int i = 0; i < mySchemes.size(); i++) {
      T existingScheme = mySchemes.get(i);
      if (existingScheme.getName().equals(scheme.getName())) {
        toReplace = i;
        break;
      }
    }
    if (toReplace == -1) {
      mySchemes.add(scheme);
    }
    else if (replaceExisting || !isExternalizable(scheme)) {
      mySchemes.set(toReplace, scheme);
    }
    else {
      //noinspection unchecked
      renameScheme((E)scheme, UniqueNameGenerator.generateUniqueName(scheme.getName(), collectExistingNames(mySchemes)));
      mySchemes.add(scheme);
    }
    onSchemeAdded(scheme);
    checkCurrentScheme(scheme);
  }

  protected void checkCurrentScheme(final Scheme scheme) {
    if (myCurrentScheme == null && myCurrentSchemeName != null && myCurrentSchemeName.equals(scheme.getName())) {
      //noinspection unchecked
      myCurrentScheme = (T)scheme;
    }
  }

  private Collection<String> collectExistingNames(final Collection<T> schemes) {
    Set<String> result = new THashSet<String>();
    for (T scheme : schemes) {
      result.add(scheme.getName());
    }
    return result;
  }

  @Override
  public void clearAllSchemes() {
    for (T myScheme : mySchemes) {
      onSchemeDeleted(myScheme);
    }
    mySchemes.clear();
  }

  @Override
  @NotNull
  public List<T> getAllSchemes() {
    return Collections.unmodifiableList(mySchemes);
  }

  @Override
  @Nullable
  public T findSchemeByName(@NotNull String schemeName) {
    for (T scheme : mySchemes) {
      if (scheme.getName().equals(schemeName)) {
        return scheme;
      }
    }
    return null;
  }

  @Override
  public void setCurrentSchemeName(final String schemeName) {
    myCurrentSchemeName = schemeName;
    myCurrentScheme = schemeName == null ? null : findSchemeByName(schemeName);
  }

  @Override
  @Nullable
  public T getCurrentScheme() {
    T currentScheme = myCurrentScheme;
    return currentScheme == null ? null : findSchemeByName(currentScheme.getName());
  }

  @Override
  public void removeScheme(@NotNull T scheme) {
    for (int i = 0, n = mySchemes.size(); i < n; i++) {
      T s = mySchemes.get(i);
      if (scheme.getName().equals(s.getName())) {
        onSchemeDeleted(s);
        mySchemes.remove(i);
        break;
      }
    }
  }

  protected void onSchemeDeleted(@NotNull Scheme toDelete) {
    if (myCurrentScheme == toDelete) {
      myCurrentScheme = null;
    }
  }

  @Override
  @NotNull
  public Collection<String> getAllSchemeNames() {
    List<String> names = new ArrayList<String>(mySchemes.size());
    for (T scheme : mySchemes) {
      names.add(scheme.getName());
    }
    return names;
  }

  protected abstract void onSchemeAdded(@NotNull T scheme);

  protected void renameScheme(@NotNull E scheme, @NotNull String newName) {
    if (!newName.equals(scheme.getName())) {
      scheme.setName(newName);
      LOG.assertTrue(newName.equals(scheme.getName()));
    }
  }

  @SuppressWarnings("deprecation")
  @NotNull
  @Override
  public Collection<SharedScheme<E>> loadSharedSchemes(Collection<T> currentSchemeList) {
    return Collections.emptyList();
  }

  @SuppressWarnings("deprecation")
  @Override
  @NotNull
  public Collection<SharedScheme<E>> loadSharedSchemes() {
    return Collections.emptyList();
  }

  @Override
  public boolean isShared(@NotNull Scheme scheme) {
    return false;
  }

  @Override
  public boolean isExportAvailable() {
    return false;
  }

  @Override
  public void exportScheme(@NotNull final E scheme, final String name, final String description) {
  }

  protected boolean isExternalizable(final T scheme) {
    return scheme instanceof ExternalizableScheme;
  }
}
