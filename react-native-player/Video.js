import React, {Component, PropTypes} from 'react';
import {StyleSheet, requireNativeComponent, NativeModules, View, PixelRatio,} from 'react-native';
import resolveAssetSource from 'react-native/Libraries/Image/resolveAssetSource';

const pixelRatio = PixelRatio.get();

const styles = StyleSheet.create({
  base: {
    overflow: 'hidden',
  },
});

export default class Video extends Component {
  constructor(props) {
    super(props);
    this.state = {
      style: StyleSheet.absoluteFill
    };
  }

  setNativeProps(nativeProps) {
    this._root.setNativeProps(nativeProps);
  }

  seek = (time) => {
    this.setNativeProps({ seek: time });
  };

  _assignRoot = (component) => {
    this._root = component;
  };

  _onLoadStart = (event) => {
    if (this.props.onLoadStart) {
      this.props.onLoadStart(event.nativeEvent);
    }
  };

  _onLoad = (event) => {
    if (this.props.onLoad) {
      this.props.onLoad(event.nativeEvent);
    }
  };

  _onError = (event) => {
    if (this.props.onError) {
      this.props.onError(event.nativeEvent);
    }
  };

  _onProgress = (event) => {
    if (this.props.onProgress) {
      this.props.onProgress(event.nativeEvent);
    }
  };

  _onPause = () => {
    if (this.props.onPause) {
      this.props.onPause();
    }
  }

  _onPlay = () => {
    if (this.props.onPlay) {
      this.props.onPlay();
    }
  }

  _onSeek = (event) => {
    if (this.props.onSeek) {
      this.props.onSeek(event.nativeEvent);
    }
  };

  _onEnd = (event) => {
    if (this.props.onEnd) {
      this.props.onEnd(event.nativeEvent);
    }
  };

  _onReadyForDisplay = (event) => {
    if (this.props.onReadyForDisplay) {
      this.props.onReadyForDisplay(event.nativeEvent);
    }
  };

  _onPlaybackStalled = (event) => {
    if (this.props.onPlaybackStalled) {
      this.props.onPlaybackStalled(event.nativeEvent);
    }
  };

  _onPlaybackResume = (event) => {
    if (this.props.onPlaybackResume) {
      this.props.onPlaybackResume(event.nativeEvent);
    }
  };

  _onNewLayout = (event) => {
    const args = event.nativeEvent;
    this.setState({
      style: {
        top: args.yoff / pixelRatio,
        bottom: args.yoff / pixelRatio,
        left: args.xoff / pixelRatio,
        right: args.xoff / pixelRatio,
      }
    });
  }

  render() {
    const source = resolveAssetSource(this.props.source) || {};

    let uri = source.uri;
    if (uri && uri.match(/^\//)) {
      uri = `file://${uri}`;
    }

    // const isNetwork = !!(uri && uri.match(/^https?:/));
    // const isAsset = !!(uri && uri.match(/^(assets-library|file|content):/));

    const nativeProps = Object.assign({}, this.props);
    Object.assign(nativeProps, {
      style: [styles.base, nativeProps.style, this.state.style],
      src: { uri },
      onVideoLoadStart: this._onLoadStart,
      onVideoLoad: this._onLoad,
      onVideoError: this._onError,
      onVideoProgress: this._onProgress,
      onVideoPause: this._onPause,
      onVideoPlay: this._onPlay,
      onVideoSeek: this._onSeek,
      onVideoEnd: this._onEnd,
      onReadyForDisplay: this._onReadyForDisplay,
      onPlaybackStalled: this._onPlaybackStalled,
      onPlaybackResume: this._onPlaybackResume,
      onNewLayout: this._onNewLayout,
    });

    return (
      <RCTVLC
        ref={this._assignRoot}
        {...nativeProps}
      />
    );
  }
}

Video.propTypes = {
  /* Native only */
  src: PropTypes.object,
  seek: PropTypes.number,
  keyControlEnabled: PropTypes.bool,

  /* Wrapper component */
  source: PropTypes.oneOfType([
    PropTypes.shape({
      uri: PropTypes.string
    }),
    // Opaque type returned by require('./video.mp4')
    PropTypes.number
  ]),
  paused: PropTypes.bool,
  volume: PropTypes.number,
  currentTime: PropTypes.number,
  onLoadStart: PropTypes.func,
  onLoad: PropTypes.func,
  onError: PropTypes.func,
  onPause: PropTypes.func,
  onPlay: PropTypes.func,
  onProgress: PropTypes.func,
  onSeek: PropTypes.func,
  onEnd: PropTypes.func,
  onReadyForDisplay: PropTypes.func,
  onPlaybackStalled: PropTypes.func,
  onPlaybackResume: PropTypes.func,

  /* Required by react-native */
  scaleX: PropTypes.number,
  scaleY: PropTypes.number,
  translateX: PropTypes.number,
  translateY: PropTypes.number,
  rotation: PropTypes.number,
  ...View.propTypes,
};

const RCTVLC = requireNativeComponent('RCTVLC', Video, {
  nativeOnly: {
    src: true,
    seek: true,
    fullscreen: true,
    keyControlEnabled: true
  },
});
