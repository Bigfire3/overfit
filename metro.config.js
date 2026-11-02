// https://docs.expo.dev/guides/customizing-metro/
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// The Anthropic SDK's ESM build contains circular imports that crash under Metro
// ("Cannot access ... before initialization"). Its CommonJS build works, so resolve to it.
const anthropicCjs = require.resolve('@anthropic-ai/sdk');
const defaultResolveRequest = config.resolver.resolveRequest;

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === '@anthropic-ai/sdk') {
    return { type: 'sourceFile', filePath: anthropicCjs };
  }
  return (defaultResolveRequest ?? context.resolveRequest)(context, moduleName, platform);
};

module.exports = config;
