// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.embedding.android;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.flutter.Log;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterShellArgs;
import io.flutter.embedding.engine.renderer.OnFirstFrameRenderedListener;
import io.flutter.plugin.platform.PlatformPlugin;
import io.flutter.view.FlutterMain;

/**
 * {@code Fragment} which displays a Flutter UI that takes up all available {@code Fragment} space.
 * <p>
 * Using a {@code FlutterFragment} requires forwarding a number of calls from an {@code Activity} to
 * ensure that the internal Flutter app behaves as expected:
 * <ol>
 *   <li>{@link #onPostResume()}</li>
 *   <li>{@link #onBackPressed()}</li>
 *   <li>{@link #onRequestPermissionsResult(int, String[], int[])} ()}</li>
 *   <li>{@link #onNewIntent(Intent)} ()}</li>
 *   <li>{@link #onUserLeaveHint()}</li>
 *   <li>{@link #onTrimMemory(int)}</li>
 * </ol>
 * Additionally, when starting an {@code Activity} for a result from this {@code Fragment}, be sure
 * to invoke {@link Fragment#startActivityForResult(Intent, int)} rather than
 * {@link android.app.Activity#startActivityForResult(Intent, int)}. If the {@code Activity} version
 * of the method is invoked then this {@code Fragment} will never receive its
 * {@link Fragment#onActivityResult(int, int, Intent)} callback.
 * <p>
 * If convenient, consider using a {@link FlutterActivity} instead of a {@code FlutterFragment} to
 * avoid the work of forwarding calls.
 * <p>
 * If Flutter is needed in a location that can only use a {@code View}, consider using a
 * {@link FlutterView}. Using a {@link FlutterView} requires forwarding some calls from an
 * {@code Activity}, as well as forwarding lifecycle calls from an {@code Activity} or a
 * {@code Fragment}.
 */
public class FlutterFragment extends Fragment implements FlutterActivityAndFragmentDelegate.Host {
  private static final String TAG = "FlutterFragment";

  /**
   * The Dart entrypoint method name that is executed upon initialization.
   */
  protected static final String ARG_DART_ENTRYPOINT = "dart_entrypoint";
  /**
   * Initial Flutter route that is rendered in a Navigator widget.
   */
  protected static final String ARG_INITIAL_ROUTE = "initial_route";
  /**
   * Path to Flutter's Dart code.
   */
  protected static final String ARG_APP_BUNDLE_PATH = "app_bundle_path";
  /**
   * Flutter shell arguments.
   */
  protected static final String ARG_FLUTTER_INITIALIZATION_ARGS = "initialization_args";
  /**
   * {@link FlutterView.RenderMode} to be used for the {@link FlutterView} in this
   * {@code FlutterFragment}
   */
  protected static final String ARG_FLUTTERVIEW_RENDER_MODE = "flutterview_render_mode";
  /**
   * {@link FlutterView.TransparencyMode} to be used for the {@link FlutterView} in this
   * {@code FlutterFragment}
   */
  protected static final String ARG_FLUTTERVIEW_TRANSPARENCY_MODE = "flutterview_transparency_mode";
  /**
   * See {@link #shouldAttachEngineToActivity()}.
   */
  protected static final String ARG_SHOULD_ATTACH_ENGINE_TO_ACTIVITY = "should_attach_engine_to_activity";

  @NonNull
  public static FlutterFragment createDefaultFlutterFragment() {
    return new FlutterFragment.Builder().build();
  }

  /**
   * Builder that creates a new {@code FlutterFragment} with {@code arguments} that correspond
   * to the values set on this {@code Builder}.
   * <p>
   * To create a {@code FlutterFragment} with default {@code arguments}, invoke
   * {@link #createDefaultFlutterFragment()}.
   * <p>
   * Subclasses of {@code FlutterFragment} that do not introduce any new arguments can use this
   * {@code Builder} to construct instances of the subclass without subclassing this {@code Builder}.
   * {@code
   *   MyFlutterFragment f = new FlutterFragment.Builder(MyFlutterFragment.class)
   *     .someProperty(...)
   *     .someOtherProperty(...)
   *     .build<MyFlutterFragment>();
   * }
   * <p>
   * Subclasses of {@code FlutterFragment} that introduce new arguments should subclass this
   * {@code Builder} to add the new properties:
   * <ol>
   *   <li>Ensure the {@code FlutterFragment} subclass has a no-arg constructor.</li>
   *   <li>Subclass this {@code Builder}.</li>
   *   <li>Override the new {@code Builder}'s no-arg constructor and invoke the super constructor
   *   to set the {@code FlutterFragment} subclass: {@code
   *     public MyBuilder() {
   *       super(MyFlutterFragment.class);
   *     }
   *   }</li>
   *   <li>Add appropriate property methods for the new properties.</li>
   *   <li>Override {@link Builder#createArgs()}, call through to the super method, then add
   *   the new properties as arguments in the {@link Bundle}.</li>
   * </ol>
   * Once a {@code Builder} subclass is defined, the {@code FlutterFragment} subclass can be
   * instantiated as follows.
   * {@code
   *   MyFlutterFragment f = new MyBuilder()
   *     .someExistingProperty(...)
   *     .someNewProperty(...)
   *     .build<MyFlutterFragment>();
   * }
   */
  public static class Builder {
    private final Class<? extends FlutterFragment> fragmentClass;
    private String dartEntrypoint = "main";
    private String initialRoute = "/";
    private String appBundlePath = null;
    private FlutterShellArgs shellArgs = null;
    private FlutterView.RenderMode renderMode = FlutterView.RenderMode.surface;
    private FlutterView.TransparencyMode transparencyMode = FlutterView.TransparencyMode.transparent;
    private boolean shouldAttachEngineToActivity = true;

    /**
     * Constructs a {@code Builder} that is configured to construct an instance of
     * {@code FlutterFragment}.
     */
    public Builder() {
      fragmentClass = FlutterFragment.class;
    }

    /**
     * Constructs a {@code Builder} that is configured to construct an instance of
     * {@code subclass}, which extends {@code FlutterFragment}.
     */
    public Builder(@NonNull Class<? extends FlutterFragment> subclass) {
      fragmentClass = subclass;
    }

    /**
     * The name of the initial Dart method to invoke, defaults to "main".
     */
    @NonNull
    public Builder dartEntrypoint(@NonNull String dartEntrypoint) {
      this.dartEntrypoint = dartEntrypoint;
      return this;
    }

    /**
     * The initial route that a Flutter app will render in this {@link FlutterFragment},
     * defaults to "/".
     */
    @NonNull
    public Builder initialRoute(@NonNull String initialRoute) {
      this.initialRoute = initialRoute;
      return this;
    }

    /**
     * The path to the app bundle which contains the Dart app to execute, defaults
     * to {@link FlutterMain#findAppBundlePath()}
     */
    @NonNull
    public Builder appBundlePath(@NonNull String appBundlePath) {
      this.appBundlePath = appBundlePath;
      return this;
    }

    /**
     * Any special configuration arguments for the Flutter engine
     */
    @NonNull
    public Builder flutterShellArgs(@NonNull FlutterShellArgs shellArgs) {
      this.shellArgs = shellArgs;
      return this;
    }

    /**
     * Render Flutter either as a {@link FlutterView.RenderMode#surface} or a
     * {@link FlutterView.RenderMode#texture}. You should use {@code surface} unless
     * you have a specific reason to use {@code texture}. {@code texture} comes with
     * a significant performance impact, but {@code texture} can be displayed
     * beneath other Android {@code View}s and animated, whereas {@code surface}
     * cannot.
     */
    @NonNull
    public Builder renderMode(@NonNull FlutterView.RenderMode renderMode) {
      this.renderMode = renderMode;
      return this;
    }

    /**
     * Support a {@link FlutterView.TransparencyMode#transparent} background within {@link FlutterView},
     * or force an {@link FlutterView.TransparencyMode#opaque} background.
     * <p>
     * See {@link FlutterView.TransparencyMode} for implications of this selection.
     */
    @NonNull
    public Builder transparencyMode(@NonNull FlutterView.TransparencyMode transparencyMode) {
      this.transparencyMode = transparencyMode;
      return this;
    }

    /**
     * Whether or not this {@code FlutterFragment} should automatically attach its
     * {@code Activity} as a control surface for its {@link FlutterEngine}.
     * <p>
     * Control surfaces are used to provide Android resources and lifecycle events to
     * plugins that are attached to the {@link FlutterEngine}. If {@code shouldAttachEngineToActivity}
     * is true then this {@code FlutterFragment} will connect its {@link FlutterEngine} to the
     * surrounding {@code Activity}, along with any plugins that are registered with that
     * {@link FlutterEngine}. This allows plugins to access the {@code Activity}, as well as
     * receive {@code Activity}-specific calls, e.g., {@link android.app.Activity#onNewIntent(Intent)}.
     * If {@code shouldAttachEngineToActivity} is false, then this {@code FlutterFragment} will not
     * automatically manage the connection between its {@link FlutterEngine} and the surrounding
     * {@code Activity}. The {@code Activity} will need to be manually connected to this
     * {@code FlutterFragment}'s {@link FlutterEngine} by the app developer. See
     * {@link FlutterEngine#getActivityControlSurface()}.
     * <p>
     * One reason that a developer might choose to manually manage the relationship between the
     * {@code Activity} and {@link FlutterEngine} is if the developer wants to move the
     * {@link FlutterEngine} somewhere else. For example, a developer might want the
     * {@link FlutterEngine} to outlive the surrounding {@code Activity} so that it can be used
     * later in a different {@code Activity}. To accomplish this, the {@link FlutterEngine} will
     * need to be disconnected from the surrounding {@code Activity} at an unusual time, preventing
     * this {@code FlutterFragment} from correctly managing the relationship between the
     * {@link FlutterEngine} and the surrounding {@code Activity}.
     * <p>
     * Another reason that a developer might choose to manually manage the relationship between the
     * {@code Activity} and {@link FlutterEngine} is if the developer wants to prevent, or explicitly
     * control when the {@link FlutterEngine}'s plugins have access to the surrounding {@code Activity}.
     * For example, imagine that this {@code FlutterFragment} only takes up part of the screen and
     * the app developer wants to ensure that none of the Flutter plugins are able to manipulate
     * the surrounding {@code Activity}. In this case, the developer would not want the
     * {@link FlutterEngine} to have access to the {@code Activity}, which can be accomplished by
     * setting {@code shouldAttachEngineToActivity} to {@code false}.
     */
    @NonNull
    public Builder shouldAttachEngineToActivity(boolean shouldAttachEngineToActivity) {
      this.shouldAttachEngineToActivity = shouldAttachEngineToActivity;
      return this;
    }

    /**
     * Creates a {@link Bundle} of arguments that are assigned to the new {@code FlutterFragment}.
     * <p>
     * Subclasses should override this method to add new properties to the {@link Bundle}. Subclasses
     * must call through to the super method to collect all existing property values.
     */
    @NonNull
    protected Bundle createArgs() {
      Bundle args = new Bundle();
      args.putString(ARG_INITIAL_ROUTE, initialRoute);
      args.putString(ARG_APP_BUNDLE_PATH, appBundlePath);
      args.putString(ARG_DART_ENTRYPOINT, dartEntrypoint);
      // TODO(mattcarroll): determine if we should have an explicit FlutterTestFragment instead of conflating.
      if (null != shellArgs) {
        args.putStringArray(ARG_FLUTTER_INITIALIZATION_ARGS, shellArgs.toArray());
      }
      args.putString(ARG_FLUTTERVIEW_RENDER_MODE, renderMode != null ? renderMode.name() : FlutterView.RenderMode.surface.name());
      args.putString(ARG_FLUTTERVIEW_TRANSPARENCY_MODE, transparencyMode != null ? transparencyMode.name() : FlutterView.TransparencyMode.transparent.name());
      args.putBoolean(ARG_SHOULD_ATTACH_ENGINE_TO_ACTIVITY, shouldAttachEngineToActivity);
      return args;
    }

    /**
     * Constructs a new {@code FlutterFragment} (or a subclass) that is configured based on
     * properties set on this {@code Builder}.
     */
    @NonNull
    public <T extends FlutterFragment> T build() {
      try {
        @SuppressWarnings("unchecked")
        T frag = (T) fragmentClass.getDeclaredConstructor().newInstance();
        if (frag == null) {
          throw new RuntimeException("The FlutterFragment subclass sent in the constructor ("
              + fragmentClass.getCanonicalName() + ") does not match the expected return type.");
        }

        Bundle args = createArgs();
        frag.setArguments(args);

        return frag;
      } catch (Exception e) {
        throw new RuntimeException("Could not instantiate FlutterFragment subclass (" + fragmentClass.getName() + ")", e);
      }
    }
  }

  // Delegate that runs all lifecycle and OS hook logic that is common between
  // FlutterActivity and FlutterFragment. See the FlutterActivityAndFragmentDelegate
  // implementation for details about why it exists.
  private FlutterActivityAndFragmentDelegate delegate;

  private final OnFirstFrameRenderedListener onFirstFrameRenderedListener = new OnFirstFrameRenderedListener() {
    @Override
    public void onFirstFrameRendered() {
      // Notify our subclasses that the first frame has been rendered.
      FlutterFragment.this.onFirstFrameRendered();

      // Notify our owning Activity that the first frame has been rendered.
      FragmentActivity fragmentActivity = getActivity();
      if (fragmentActivity instanceof OnFirstFrameRenderedListener) {
        OnFirstFrameRenderedListener activityAsListener = (OnFirstFrameRenderedListener) fragmentActivity;
        activityAsListener.onFirstFrameRendered();
      }
    }
  };

  public FlutterFragment() {
    // Ensure that we at least have an empty Bundle of arguments so that we don't
    // need to continually check for null arguments before grabbing one.
    setArguments(new Bundle());
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    delegate = new FlutterActivityAndFragmentDelegate(this);
    delegate.onAttach(context);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return delegate.onCreateView(inflater, container, savedInstanceState);
  }

  @Override
  public void onStart() {
    super.onStart();
    delegate.onStart();
  }

  @Override
  public void onResume() {
    super.onResume();
    delegate.onResume();
  }

  // TODO(mattcarroll): determine why this can't be in onResume(). Comment reason, or move if possible.
  @ActivityCallThrough
  public void onPostResume() {
    delegate.onPostResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    delegate.onPause();
  }

  @Override
  public void onStop() {
    super.onStop();
    delegate.onStop();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    delegate.onDestroyView();
  }

  @Override
  public void onDetach() {
    super.onDetach();
    delegate.onDetach();
    delegate.release();
    delegate = null;
  }

  /**
   * The result of a permission request has been received.
   * <p>
   * See {@link android.app.Activity#onRequestPermissionsResult(int, String[], int[])}
   * <p>
   * @param requestCode identifier passed with the initial permission request
   * @param permissions permissions that were requested
   * @param grantResults permission grants or denials
   */
  @ActivityCallThrough
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    delegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  /**
   * A new Intent was received by the {@link android.app.Activity} that currently owns this
   * {@link Fragment}.
   * <p>
   * See {@link android.app.Activity#onNewIntent(Intent)}
   * <p>
   * @param intent new Intent
   */
  @ActivityCallThrough
  public void onNewIntent(@NonNull Intent intent) {
    delegate.onNewIntent(intent);
  }

  /**
   * The hardware back button was pressed.
   * <p>
   * See {@link android.app.Activity#onBackPressed()}
   */
  @ActivityCallThrough
  public void onBackPressed() {
    delegate.onBackPressed();
  }

  /**
   * A result has been returned after an invocation of {@link Fragment#startActivityForResult(Intent, int)}.
   * <p>
   * @param requestCode request code sent with {@link Fragment#startActivityForResult(Intent, int)}
   * @param resultCode code representing the result of the {@code Activity} that was launched
   * @param data any corresponding return data, held within an {@code Intent}
   */
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    delegate.onActivityResult(requestCode, resultCode, data);
  }

  /**
   * The {@link android.app.Activity} that owns this {@link Fragment} is about to go to the background
   * as the result of a user's choice/action, i.e., not as the result of an OS decision.
   * <p>
   * See {@link android.app.Activity#onUserLeaveHint()}
   */
  @ActivityCallThrough
  public void onUserLeaveHint() {
    delegate.onUserLeaveHint();
  }

  /**
   * Callback invoked when memory is low.
   * <p>
   * This implementation forwards a memory pressure warning to the running Flutter app.
   * <p>
   * @param level level
   */
  @ActivityCallThrough
  public void onTrimMemory(int level) {
    delegate.onTrimMemory(level);
  }

  /**
   * Callback invoked when memory is low.
   * <p>
   * This implementation forwards a memory pressure warning to the running Flutter app.
   */
  @Override
  public void onLowMemory() {
    super.onLowMemory();
    delegate.onLowMemory();
  }

  @NonNull
  private Context getContextCompat() {
    return Build.VERSION.SDK_INT >= 23
      ? getContext()
      : getActivity();
  }

  /**
   * {@link FlutterActivityAndFragmentDelegate.Host} method that is used by
   * {@link FlutterActivityAndFragmentDelegate} to obtain Flutter shell arguments when
   * initializing Flutter.
   */
  @Override
  @NonNull
  public FlutterShellArgs getFlutterShellArgs() {
    String[] flutterShellArgsArray = getArguments().getStringArray(ARG_FLUTTER_INITIALIZATION_ARGS);
    return new FlutterShellArgs(
        flutterShellArgsArray != null ? flutterShellArgsArray : new String[] {}
    );
  }

  /**
   * Returns the name of the Dart method that this {@code FlutterFragment} should execute to
   * start a Flutter app.
   * <p>
   * Defaults to "main".
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  @NonNull
  public String getDartEntrypointFunctionName() {
    return getArguments().getString(ARG_DART_ENTRYPOINT, "main");
  }

  /**
   * Returns the file path to the desired Flutter app's bundle of code.
   * <p>
   * Defaults to {@link FlutterMain#findAppBundlePath()}.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  @NonNull
  public String getAppBundlePath() {
    return getArguments().getString(ARG_APP_BUNDLE_PATH, FlutterMain.findAppBundlePath());
  }

  /**
   * Returns the initial route that should be rendered within Flutter, once the Flutter app starts.
   * <p>
   * Defaults to {@code null}, which signifies a route of "/" in Flutter.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  @Nullable
  public String getInitialRoute() {
    return getArguments().getString(ARG_INITIAL_ROUTE);
  }

  /**
   * Returns the desired {@link FlutterView.RenderMode} for the {@link FlutterView} displayed in
   * this {@code FlutterFragment}.
   * <p>
   * Defaults to {@link FlutterView.RenderMode#surface}.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  @NonNull
  public FlutterView.RenderMode getRenderMode() {
    String renderModeName = getArguments().getString(
        ARG_FLUTTERVIEW_RENDER_MODE,
        FlutterView.RenderMode.surface.name()
    );
    return FlutterView.RenderMode.valueOf(renderModeName);
  }

  /**
   * Returns the desired {@link FlutterView.TransparencyMode} for the {@link FlutterView} displayed in
   * this {@code FlutterFragment}.
   * <p>
   * Defaults to {@link FlutterView.TransparencyMode#transparent}.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  @NonNull
  public FlutterView.TransparencyMode getTransparencyMode() {
    String transparencyModeName = getArguments().getString(
        ARG_FLUTTERVIEW_TRANSPARENCY_MODE,
        FlutterView.TransparencyMode.transparent.name()
    );
    return FlutterView.TransparencyMode.valueOf(transparencyModeName);
  }

  @Override
  @Nullable
  public SplashScreen provideSplashScreen() {
    FragmentActivity parentActivity = getActivity();
    if (parentActivity instanceof SplashScreenProvider) {
      SplashScreenProvider splashScreenProvider = (SplashScreenProvider) parentActivity;
      return splashScreenProvider.provideSplashScreen();
    }

    return null;
  }

  /**
   * Hook for subclasses to return a {@link FlutterEngine} with whatever configuration
   * is desired.
   * <p>
   * By default this method defers to this {@code FlutterFragment}'s surrounding {@code Activity},
   * if that {@code Activity} implements {@link FlutterEngineProvider}. If this method is
   * overridden, the surrounding {@code Activity} will no longer be given an opportunity to
   * provide a {@link FlutterEngine}, unless the subclass explicitly implements that behavior.
   * <p>
   * Consider returning a cached {@link FlutterEngine} instance from this method to avoid the
   * typical warm-up time that a new {@link FlutterEngine} instance requires.
   * <p>
   * If null is returned then a new default {@link FlutterEngine} will be created to back this
   * {@code FlutterFragment}.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  @Nullable
  public FlutterEngine provideFlutterEngine(@NonNull Context context) {
    // Defer to the FragmentActivity that owns us to see if it wants to provide a
    // FlutterEngine.
    FlutterEngine flutterEngine = null;
    FragmentActivity attachedActivity = getActivity();
    if (attachedActivity instanceof FlutterEngineProvider) {
      // Defer to the Activity that owns us to provide a FlutterEngine.
      Log.d(TAG, "Deferring to attached Activity to provide a FlutterEngine.");
      FlutterEngineProvider flutterEngineProvider = (FlutterEngineProvider) attachedActivity;
      flutterEngine = flutterEngineProvider.provideFlutterEngine(getContext());
    }

    return flutterEngine;
  }

  /**
   * Hook for subclasses to obtain a reference to the {@link FlutterEngine} that is owned
   * by this {@code FlutterActivity}.
   */
  @Nullable
  public FlutterEngine getFlutterEngine() {
    return delegate.getFlutterEngine();
  }

  @Nullable
  @Override
  public PlatformPlugin providePlatformPlugin(@Nullable Activity activity, @NonNull FlutterEngine flutterEngine) {
    if (activity != null) {
      return new PlatformPlugin(getActivity(), flutterEngine.getPlatformChannel());
    } else {
      return null;
    }
  }

  /**
   * Configures a {@link FlutterEngine} after its creation.
   * <p>
   * This method is called after {@link #provideFlutterEngine(Context)}, and after the given
   * {@link FlutterEngine} has been attached to the owning {@code FragmentActivity}. See
   * {@link io.flutter.embedding.engine.plugins.activity.ActivityControlSurface#attachToActivity(Activity, Lifecycle)}.
   * <p>
   * It is possible that the owning {@code FragmentActivity} opted not to connect itself as
   * an {@link io.flutter.embedding.engine.plugins.activity.ActivityControlSurface}. In that
   * case, any configuration, e.g., plugins, must not expect or depend upon an available
   * {@code Activity} at the time that this method is invoked.
   * <p>
   * The default behavior of this method is to defer to the owning {@code FragmentActivity}
   * as a {@link FlutterEngineConfigurator}. Subclasses can override this method if the
   * subclass needs to override the {@code FragmentActivity}'s behavior, or add to it.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
    FragmentActivity attachedActivity = getActivity();
    if (attachedActivity instanceof FlutterEngineConfigurator) {
      ((FlutterEngineConfigurator) attachedActivity).configureFlutterEngine(flutterEngine);
    }
  }

  /**
   * See {@link Builder#shouldAttachEngineToActivity()}.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  public boolean shouldAttachEngineToActivity() {
    return getArguments().getBoolean(ARG_SHOULD_ATTACH_ENGINE_TO_ACTIVITY);
  }

  /**
   * Returns true if the {@link FlutterEngine} within this {@code FlutterFragment} should outlive
   * the {@code FlutterFragment}, itself.
   * <p>
   * Defaults to false. This method can be overridden in subclasses to retain the
   * {@link FlutterEngine}.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  // TODO(mattcarroll): consider a dynamic determination of this preference based on whether the
  //                    engine was created automatically, or if the engine was provided manually.
  //                    Manually provided engines should probably not be destroyed.
  @Override
  public boolean retainFlutterEngineAfterHostDestruction() {
    return false;
  }

  /**
   * Invoked after the {@link FlutterView} within this {@code FlutterFragment} renders its first
   * frame.
   * <p>
   * This method forwards {@code onFirstFrameRendered()} to its attached {@code Activity}, if
   * the attached {@code Activity} implements {@link OnFirstFrameRenderedListener}.
   * <p>
   * Subclasses that override this method must call through to the {@code super} method.
   * <p>
   * Used by this {@code FlutterFragment}'s {@link FlutterActivityAndFragmentDelegate.Host}
   */
  @Override
  public void onFirstFrameRendered() {
    FragmentActivity attachedActivity = getActivity();
    if (attachedActivity instanceof OnFirstFrameRenderedListener) {
      ((OnFirstFrameRenderedListener) attachedActivity).onFirstFrameRendered();
    }
  }

  /**
   * Annotates methods in {@code FlutterFragment} that must be called by the containing
   * {@code Activity}.
   */
  @interface ActivityCallThrough {}

}
